/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine.flink.execution;

import java.io.Serializable;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import com.ibm.cohort.engine.flink.KafkaCommon;
import com.ibm.cohort.engine.flink.KafkaInfo;
import com.ibm.cohort.engine.flink.MeasureExecution;
import com.ibm.cohort.engine.measure.FHIRClientContext;
import com.ibm.cohort.engine.measure.MeasureContext;
import com.ibm.cohort.engine.measure.MeasureEvaluator;
import com.ibm.cohort.engine.measure.R4MeasureEvaluatorBuilder;
import com.ibm.cohort.engine.measure.cache.RetrieveCacheKey;
import com.ibm.cohort.engine.measure.cache.RetrieveCacheContext;
import com.ibm.cohort.engine.measure.cache.DefaultRetrieveCacheContext;
import com.ibm.cohort.engine.measure.evidence.MeasureEvidenceOptions;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilderFactory;
import com.ibm.cohort.fhir.client.config.FhirClientBuilder;
import com.ibm.cohort.fhir.client.config.FhirClientBuilderFactory;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Collector;
import org.hl7.fhir.r4.model.MeasureReport;

public class CohortEngineFlinkDriver implements Serializable {

	private static final long serialVersionUID = 1966474691011266880L;

	// Remove this when adding proper evidence support to the flink job
	private static final MeasureEvidenceOptions NO_EVIDENCE_OPTIONS = new MeasureEvidenceOptions();

	public static void main(String[] args) throws Exception {
		ParameterTool params = ParameterTool.fromArgs(args);

		FHIRServerInfo fhirServerInfo = new FHIRServerInfo(
				params.getRequired("fhirTenantId"),
				params.getRequired("fhirUsername"),
				params.getRequired("fhirPassword"),
				params.getRequired("fhirEndpoint")
		);

		KafkaInfo kafkaInputInfo = new KafkaInfo(
				params.getRequired("kafkaBrokers"),
				params.getRequired("kafkaInputTopic"),
				params.getRequired("kafkaPassword")
		);

		KafkaInfo kafkaOutputInfo = null;
		if (params.has("kafkaOutputTopic")) {
			kafkaOutputInfo = new KafkaInfo(
					params.getRequired("kafkaBrokers"),
					params.getRequired("kafkaOutputTopic"),
					params.getRequired("kafkaPassword")
			);
		}

		boolean enableCache = params.has("enableCache");
		CacheConfiguration cacheConfiguration = new CacheConfiguration(
				params.getInt("cacheMaxSize", 1_000),
				params.getInt("cacheExpireOnWrite", 300),
				params.has("cacheEnableStatistics")
		);

		CohortEngineFlinkDriver example = new CohortEngineFlinkDriver(
				fhirServerInfo,
				enableCache ? cacheConfiguration : null
		);
		example.run(
				params.get("jobName", "cohort-engine"),
				params.getRequired("kafkaGroupId"),
				kafkaInputInfo,
				kafkaOutputInfo,
				params.has("printOutputToConsole"),
				params.has("rebalanceInput"),
				params.has("readFromStart")
		);
	}

	private final FHIRServerInfo fhirServerInfo;
	private final CacheConfiguration cacheConfiguration;

	private transient MeasureEvaluator evaluator;
	private transient FhirContext fhirContext;
	private transient ObjectMapper objectMapper;
	private transient RetrieveCacheContext cacheContext;

	public CohortEngineFlinkDriver(FHIRServerInfo fhirServerInfo, CacheConfiguration cacheConfiguration) {
		this.fhirServerInfo = fhirServerInfo;
		this.cacheConfiguration = cacheConfiguration;
	}

	private void run(
			String jobName,
			String kafkaGroupId,
			KafkaInfo kafkaInputInfo,
			KafkaInfo kafkaOutputInfo,
			boolean printOutputToConsole,
			boolean rebalanceInput,
			boolean readFromStart
	) throws Exception {
		FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>(
				kafkaInputInfo.getTopic(),
				new SimpleStringSchema(),
				KafkaCommon.getConsumerProperties(kafkaInputInfo, kafkaGroupId)
		);
		if (readFromStart) {
			consumer.setStartFromEarliest();
		}

		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		DataStream<String> stream = env.addSource(consumer);
		if (rebalanceInput) {
			stream = stream.rebalance();
		}

		stream = stream
				.map(this::deserializeMeasureExecution)
				.map(this::evaluate)
				.flatMap((List<String> measureReports, Collector<String> collector) -> measureReports.forEach(collector::collect))
				.returns(String.class);

		if (printOutputToConsole) {
			stream.print();
		}

		if (kafkaOutputInfo != null) {
			FlinkKafkaProducer<String> producer = new FlinkKafkaProducer<>(
					kafkaOutputInfo.getTopic(),
					new SimpleStringSchema(),
					KafkaCommon.getProducerProperties(kafkaOutputInfo)
			);
			stream.addSink(producer);
		}

		env.execute(jobName);
	}

	private List<String> evaluate(MeasureExecution execution) {
		MeasureEvaluator evaluator = getEvaluator();

		List<MeasureContext> measureContexts = execution.getMeasureIds().stream()
				.map(MeasureContext::new)
				.collect(Collectors.toList());

		List<MeasureReport> result = evaluator.evaluatePatientMeasures(
				execution.getPatientId(),
				measureContexts,
				NO_EVIDENCE_OPTIONS
		);

		FhirContext fhirContext = getFhirContext();
		IParser jsonParser = fhirContext.newJsonParser();

		return result.stream()
				.map(jsonParser::encodeResourceToString)
				.collect(Collectors.toList());
	}

	private ObjectMapper getObjectMapper() {
		if (objectMapper == null) {
			objectMapper = new ObjectMapper();
		}
		return objectMapper;
	}

	private FhirContext getFhirContext() {
		if (fhirContext == null) {
			fhirContext = FhirContext.forR4();
		}
		return fhirContext;
	}

	private MeasureEvaluator getEvaluator() {
		if (evaluator == null) {
			evaluator = createEvaluator();
		}
		return evaluator;
	}

	private RetrieveCacheContext getCacheContext() {
		if (cacheContext == null) {
			cacheContext = createCacheContext();
		}
		return cacheContext;
	}

	private MeasureEvaluator createEvaluator() {
		FhirClientBuilderFactory factory = new DefaultFhirClientBuilderFactory();

		FhirClientBuilder clientBuilder = factory.newFhirClientBuilder(getFhirContext());
		IGenericClient genericClient = clientBuilder.createFhirClient(fhirServerInfo.toIbmServerConfig());

		FHIRClientContext clientContext = new FHIRClientContext.Builder()
				.withDefaultClient(genericClient)
				.build();
		R4MeasureEvaluatorBuilder evalBuilder = new R4MeasureEvaluatorBuilder().withClientContext(clientContext);
		if (cacheConfiguration != null) {
			evalBuilder.withRetrieveCacheContext(getCacheContext());
		}
		return evalBuilder.build();
	}

	private MeasureExecution deserializeMeasureExecution(String input) throws Exception {
		ObjectMapper mapper = getObjectMapper();
		return mapper.readValue(input, MeasureExecution.class);
	}

	private RetrieveCacheContext createCacheContext() {
		CaffeineConfiguration<RetrieveCacheKey, Iterable<Object>> cacheConfig = new CaffeineConfiguration<>();
		cacheConfig.setMaximumSize(OptionalLong.of(cacheConfiguration.getMaxSize()));
		cacheConfig.setExpireAfterWrite(OptionalLong.of(TimeUnit.SECONDS.toNanos(cacheConfiguration.getExpireOnWrite())));
		cacheConfig.setStatisticsEnabled(cacheConfiguration.isEnableStatistics());
		return new DefaultRetrieveCacheContext(cacheConfig);
	}

}
