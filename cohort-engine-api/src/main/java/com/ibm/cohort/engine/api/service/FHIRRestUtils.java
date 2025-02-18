/*
 * (C) Copyright IBM Corp. 2021, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.api.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.core.HttpHeaders;

import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.ParameterDefinition;
import org.hl7.fhir.r4.model.ParameterDefinition.ParameterUse;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cohort.engine.api.service.model.MeasureParameterInfo;
import com.ibm.cohort.engine.measure.RestFhirMeasureResolutionProvider;
import com.ibm.cohort.engine.measure.seed.MeasureEvaluationSeeder;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilder;
import com.ibm.cohort.fhir.client.config.IBMFhirServerConfig;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

/**
 * A set of methods that can be re-used across REST calls
 *
 */
public class FHIRRestUtils {

	private static final List<String> complicatedTypes = new ArrayList<>();
	static {
		complicatedTypes.add("Period");
		complicatedTypes.add("Range");
		complicatedTypes.add("Quantity");
	}
	/**
	 * @param fhirEndpoint The REST endpoint for the FHIR server
	 * @param fhirTenantIdHeader the header used by FHIR to identify the tenant
	 * @param fhirTenantId the actual FHIR tenant id
	 * @param fhirDataSourceIdHeader The header used by FHIR to identify the datasource
	 * @param fhirDataSourceId the actual FHIR datasource name
	 * @param httpHeaders HttpHeaders which contain the AUTHORIZATION header which contains either username/password or OAuth token
	 * @return IGenericClient A client that can be used to make calls to the FHIR server
	 * 
	 * Convenience method to get a FHIR client
	 */
	public static IGenericClient getFHIRClient(String fhirEndpoint, String fhirTenantIdHeader, String fhirTenantId, String fhirDataSourceIdHeader, String fhirDataSourceId, HttpHeaders httpHeaders) throws IllegalArgumentException {
		// FHIR credentials are passed using HTTP basic authentication and are base 64 encoded
		// parse the username/password and handle any errors. authParts contains the username
		// in the first element and password in the second
		// OR
		// An OAuth Bearer token could be passed instead of username/password, if so
		// authParts just contains the OAuth bearer token.
		String[] authParts = FHIRRestUtils.parseAuthenticationHeaderInfo(httpHeaders);
		
		if(authParts.length == 1) {
			//If only 1 entry, then it is the OAuth token
			return FHIRRestUtils.getFHIRClient(fhirEndpoint, authParts[0], fhirTenantIdHeader, fhirTenantId, fhirDataSourceIdHeader, fhirDataSourceId);
		} else {
			//If 2 entries, it is username/password
			return FHIRRestUtils.getFHIRClient(fhirEndpoint, authParts[0], authParts[1], fhirTenantIdHeader, fhirTenantId, fhirDataSourceIdHeader, fhirDataSourceId);
		}
	}
	
	
	/**
	 * @param fhirEndpoint The REST endpoint for the FHIR server
	 * @param userName FHIR server username
	 * @param password FHIR server password
	 * @param fhirTenantIdHeader the header used by FHIR to identify the tenant
	 * @param fhirTenantId the actual FHIR tenant id
	 * @param fhirDataSourceIdHeader The header used by FHIR to identify the datasource
	 * @param fhirDataSourceId the actual FHIR datasource name
	 * @return IGenericClient A client that can be used to make calls to the FHIR server
	 * 
	 * Convenience method to get a FHIR client
	 */
	public static IGenericClient getFHIRClient(String fhirEndpoint, String userName, String password, String fhirTenantIdHeader, String fhirTenantId, String fhirDataSourceIdHeader, String fhirDataSourceId) {
		IBMFhirServerConfig config = getPartialFHIRConfig(fhirEndpoint, fhirTenantIdHeader, fhirTenantId, fhirDataSourceIdHeader, fhirDataSourceId);
		config.setUser(userName);
		config.setPassword(password);

		FhirContext ctx = FhirContext.forR4();
		DefaultFhirClientBuilder builder = new DefaultFhirClientBuilder(ctx);
		
		return builder.createFhirClient(config);
	}
	
	/**
	 * @param fhirEndpoint The REST endpoint for the FHIR server
	 * @param bearerToken OAuth token value
	 * @param fhirTenantIdHeader the header used by FHIR to identify the tenant
	 * @param fhirTenantId the actual FHIR tenant id
	 * @param fhirDataSourceIdHeader The header used by FHIR to identify the datasource
	 * @param fhirDataSourceId the actual FHIR datasource name
	 * @return IGenericClient A client that can be used to make calls to the FHIR server
	 * 
	 * Convenience method to get a FHIR client
	 */
	public static IGenericClient getFHIRClient(String fhirEndpoint, String bearerToken, String fhirTenantIdHeader, String fhirTenantId, String fhirDataSourceIdHeader, String fhirDataSourceId) {
		IBMFhirServerConfig config = getPartialFHIRConfig(fhirEndpoint, fhirTenantIdHeader, fhirTenantId, fhirDataSourceIdHeader, fhirDataSourceId);
		config.setToken(bearerToken);

		FhirContext ctx = FhirContext.forR4();
		DefaultFhirClientBuilder builder = new DefaultFhirClientBuilder(ctx);
		
		return builder.createFhirClient(config);
	}
	
	private static IBMFhirServerConfig getPartialFHIRConfig(String fhirEndpoint, String fhirTenantIdHeader, String fhirTenantId, String fhirDataSourceIdHeader, String fhirDataSourceId) {
		IBMFhirServerConfig config = new IBMFhirServerConfig();
		config.setEndpoint(fhirEndpoint);
		
		if(fhirTenantIdHeader == null || fhirTenantIdHeader.trim().isEmpty()) {
			fhirTenantIdHeader = IBMFhirServerConfig.DEFAULT_TENANT_ID_HEADER;
		}
		config.setTenantIdHeader(fhirTenantIdHeader);
		config.setTenantId(fhirTenantId);

		if(fhirDataSourceIdHeader == null || fhirDataSourceIdHeader.trim().isEmpty()) {
			fhirDataSourceIdHeader = IBMFhirServerConfig.DEFAULT_DATASOURCE_ID_HEADER;
		}
		config.setDataSourceIdHeader(fhirDataSourceIdHeader);
		config.setDataSourceId(fhirDataSourceId);
		
		return config;
	}
	
	/**
	 * @param measureClient A client that can be used to make calls to the FHIR server
	 * @param identifier Identifier object which describes the measure
	 * @param measureVersion The version of the measure we want to retrieve
	 * @return A list containing parameter info for all the parameters 
	 * 
	 * Get a list of MeasureParameterInfo objects which are used to return results via the REST API.
	 * Objects describe the parameters for libraries linked to by the Measure resource
	 * if measureVersion is an empty string, the underlying code will attempt to resolve the latest
	 * measure version using semantic versioning
	 */
	public static List<MeasureParameterInfo> getParametersForMeasureIdentifier(IGenericClient measureClient, Identifier identifier, String measureVersion) {
		Measure measure = Optional.of(new RestFhirMeasureResolutionProvider(measureClient))
				.map(provider -> provider.resolveMeasureByIdentifier(identifier, measureVersion))
				.orElseThrow(() -> new ResourceNotFoundException("Measure resource not found for identifier: " + identifier + ", version: " + measureVersion));

		return getParameters(measure);
	}
	
	/**
	 * @param measureClient A client that cna be used to make calls to the FHIR server
	 * @param measureId THe FHIR resource id used to identify the measure
	 * @return A list containing parameter info for all the parameters
	 * 
	 * Get a list of MeasureParameterInfo objects which are used to return results via the REST API.
	 * Objects describe the parameters for libraries linked to by the FHIR Measure resource id
	 */
	public static List<MeasureParameterInfo> getParametersForMeasureId(IGenericClient measureClient, String measureId) {
		Measure measure = Optional.of(new RestFhirMeasureResolutionProvider(measureClient))
				.map(provider -> provider.resolveMeasureById(measureId))
				.orElseThrow(() -> new ResourceNotFoundException("Measure resource not found for id: " + measureId));

		return getParameters(measure);
	}

	private static List<MeasureParameterInfo> getParameters(Measure measure) {
		return measure.getExtension().stream()
				.filter(MeasureEvaluationSeeder::isMeasureParameter)
				.map(Extension::getValue)
				.map(ParameterDefinition.class::cast) // enforced as part of IG
				.map(FHIRRestUtils::toMeasureParameterInfo)
				.collect(Collectors.toList());
	}

	private static MeasureParameterInfo toMeasureParameterInfo(ParameterDefinition parameterDefinition) {

		MeasureParameterInfo retVal = new MeasureParameterInfo();
		String defaultValue = complicatedTypes.contains(parameterDefinition.getType())? complicatedTypeValueConstructor(parameterDefinition) : null;

		retVal.name(parameterDefinition.getName())
				.type(parameterDefinition.getType())
				.min(parameterDefinition.getMin())
				.max(parameterDefinition.getMax())
				.documentation(parameterDefinition.getDocumentation());

		Optional.ofNullable(parameterDefinition.getUse())
				.map(ParameterUse::getDisplay)
				.ifPresent(retVal::setUse);

		parameterDefinition.getExtension().stream()
				.filter(MeasureEvaluationSeeder::isDefaultValue).findFirst() // only zero or one per IG
				.map(Extension::getValue)
				.map(x -> {if(defaultValue != null){return defaultValue;} else return x.toString();})
				.ifPresent(retVal::setDefaultValue);

		return retVal;
	}

	static String complicatedTypeValueConstructor(ParameterDefinition parameterDefinition) {
		FhirContext context = FhirContext.forCached(FhirVersionEnum.R4);
		IParser parser = context.newJsonParser();
		String valueKey;
		//In order to use the hapi parser, we cannot translate an extension by itself. The patient object wraps the extension
		Patient patient = new Patient();
		Extension extension = new Extension();
		switch (parameterDefinition.getType()) {
			case "Period":
				Period period = (Period) parameterDefinition.getExtension().get(0).getValue();
				extension.setValue(period);
				patient.addExtension(extension);
				valueKey = "valuePeriod";
				break;
			case "Range":
				Range range = (Range) parameterDefinition.getExtension().get(0).getValue();
				extension.setValue(range);
				patient.addExtension(extension);
				valueKey = "valueRange";
				break;
			case "Quantity":
				Quantity quantity = (Quantity) parameterDefinition.getExtension().get(0).getValue();
				extension.setValue(quantity);
				patient.addExtension(extension);
				valueKey = "valueQuantity";
				break;
			default:
				throw new RuntimeException("Complicated Type" + parameterDefinition.getType() + " not yet implemented");
		}
		String intermediateValue = parser.encodeResourceToString(patient);
		ObjectMapper mapper = new ObjectMapper();
		try {
			JsonNode root = mapper.readTree(intermediateValue);
			JsonNode nameNode = root.path("extension");
			return nameNode.get(0).path(valueKey).toString();
		}
		catch (JsonProcessingException e){
			throw new RuntimeException("There was an issue with json translation", e);
		}
	}

	/**
	 * @param httpHeaders The HttpHeaders from the request we want to parse
	 * @return String[] containing username as first element and password as second
	 * @throws IllegalArgumentException thrown if the information in the headers is bad
	 */
	public static String[] parseAuthenticationHeaderInfo(HttpHeaders httpHeaders) throws IllegalArgumentException {
		List<String> headers = httpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION);
		if(headers == null || headers.isEmpty()) {
			throw new IllegalArgumentException("Missing HTTP authorization header information. FHIR server credentials must be passed using HTTP Basic Authentication");
		}
		String authString = headers.get(0);
		if(authString == null || authString.trim().isEmpty()) {
			throw new IllegalArgumentException("No data in HTTP authorization header. FHIR server credentials must be passed using HTTP Basic Authentication");
		}
		
		//If OAuth authentication is being used, parse the bearer token value from the headers and return it
		if(authString.startsWith("Bearer ")) {
			return new String[] {authString.substring("Bearer ".length())};
		}
		//HTTP basic authentication headers are passed in as a base64 encoded string so we need to decode it
		String decodedAuthStr = FHIRRestUtils.decodeAuthenticationString(authString);
		//returned string will be in format of username:password
		String[] authParts = decodedAuthStr.split(":");
		if(authParts.length < 2) {
			throw new IllegalArgumentException("Missing data in HTTP authorization header. FHIR server credentials must be passed using HTTP Basic Authentication");
		}
		
		return authParts;
	}
	
	/**
	 * @param authString a base64 encoded HTTP basic authentication header string
	 * @return decoded string value
	 */
	public static String decodeAuthenticationString(String authString) {
		String decodedAuth = "";
		// Basic authentication header is in the format "Basic 5tyc0uiDat4" using base 64 encoding
		// We need to extract data before decoding it back to original string
		try {
			String[] authParts = authString.split("\\s+");
			String authInfo = authParts[1];
			// Decode the data back to original string
			byte[] bytes = null;
		
			bytes = Base64.getDecoder().decode(authInfo);
			decodedAuth = new String(bytes);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error decoding base 64 HTTP authorization header information. User:password string must be properly base 64 encoded", e);
		}
		return decodedAuth;
	}
	
}
