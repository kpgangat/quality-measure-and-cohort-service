/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.translator.provider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXB;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslator.Options;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.FhirLibrarySourceProvider;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.fhir.ucum.UcumService;
import org.hl7.elm.r1.VersionedIdentifier;
import org.hl7.elm_modelinfo.r1.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.cohort.file.LibraryFormat;

/**
 * Uses the CqlTranslator inprocess to convert CQL to ELM. 
 */
public class InJVMCqlTranslationProvider extends BaseCqlTranslationProvider {

	private static final Logger LOG = LoggerFactory.getLogger(InJVMCqlTranslationProvider.class);
	private ModelManager modelManager;
	private LibraryManager libraryManager;
	
	private Map<VersionedIdentifier, ModelInfo> customModels = new HashMap<>();

	public InJVMCqlTranslationProvider() {
		this.modelManager = new ModelManager();
		modelManager.getModelInfoLoader().registerModelInfoProvider( (vid) -> customModels.get(vid) );
		
		this.libraryManager = new LibraryManager(modelManager);
		libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
	}

	
	public InJVMCqlTranslationProvider(LibrarySourceProvider provider) {
		this();
		addLibrarySourceProvider(provider);
	}

	public InJVMCqlTranslationProvider addLibrarySourceProvider(LibrarySourceProvider provider) {
		libraryManager.getLibrarySourceLoader().registerProvider(provider);
		return this;
	}

	@Override
	public String translate(InputStream cql, List<Options> options, LibraryFormat targetFormat) throws Exception {
		String result;

		UcumService ucumService = null;
		LibraryBuilder.SignatureLevel signatureLevel = LibraryBuilder.SignatureLevel.None;

		List<Options> optionsList = new ArrayList<>();
		if (options != null) {
			optionsList.addAll(options);
		}

		CqlTranslator translator = CqlTranslator.fromStream(cql, modelManager, libraryManager, ucumService,
				CqlTranslatorException.ErrorSeverity.Info, signatureLevel,
				optionsList.toArray(new Options[optionsList.size()]));

		LOG.debug("Translated CQL contains {} errors", translator.getErrors().size());
		if (!translator.getErrors().isEmpty()) {
			throw new Exception("CQL translation contained errors: " + formatMsg(translator.getErrors()));
		}

		LOG.debug("Translated CQL contains {} exceptions", translator.getExceptions().size());
		if (!translator.getExceptions().isEmpty()) {
			throw new Exception("CQL translation contained exceptions: " + formatMsg(translator.getExceptions()));
		}
		
		if (!translator.getWarnings().isEmpty()) {
			LOG.warn("Translated CQL contains warnings: " + formatMsg(translator.getWarnings()));
		}

		switch (targetFormat) {
		case XML:
			result = translator.toXml();
			break;
// This is only a theoretical nice-to-have and fails deserialization, so disabling support for now.
//		case JSON:
//			result = JsonCqlLibraryReader.read(new StringReader(translator.toJxson()));
//			break;
		default:
			throw new IllegalArgumentException(
					String.format("The CQL Engine does not support format %s", targetFormat.name()));
		}

		return result;
	}

	@Override
	public String translate(String cql, List<Options> options, LibraryFormat targetFormat) throws Exception {
		return translate(new ByteArrayInputStream(cql.getBytes()), options, targetFormat);
	}

	@Override
	public void registerModelInfo(ModelInfo modelInfo) {
		// Force mapping  to FHIR 4.0.1. Consider supporting different versions in the future.
		// Possibly add support for auto-loading model info files.
		modelInfo.setTargetVersion("4.0.1");
		modelInfo.setTargetUrl("http://hl7.org/fhir");
		VersionedIdentifier modelId = new VersionedIdentifier().withId(modelInfo.getName()).withVersion(modelInfo.getVersion());
		customModels.put(modelId,  modelInfo);
	}

	@Override
	public ModelInfo convertToModelInfo(InputStream modelInfoInputStream) {
		return JAXB.unmarshal(modelInfoInputStream, ModelInfo.class);
	}

	@Override
	public ModelInfo convertToModelInfo(Reader modelInfoReader) {
		return JAXB.unmarshal(modelInfoReader, ModelInfo.class);
	}

	@Override
	public ModelInfo convertToModelInfo(File modelInfoFile) {
		return JAXB.unmarshal(modelInfoFile, ModelInfo.class);
	}
	
    /**
     * Some of this was adapted from the CQL Translation Server TranslationFailureException.
     * 
     * @param translationErrs List of translation errors.
     * @return String representation of the list of translation errors.
     */
    private static String formatMsg(List<CqlTranslatorException> translationErrs) {
        StringBuilder msg = new StringBuilder();
        for (CqlTranslatorException error : translationErrs) {
          TrackBack tb = error.getLocator();
          String lines = tb == null ? "[n/a]" : String.format("[%s:%s (start:%d:%d, end:%d:%d)]",
                  tb.getLibrary().getId(), tb.getLibrary().getVersion(),
                  tb.getStartLine(), tb.getStartChar(), tb.getEndLine(),
                  tb.getEndChar());
          msg.append(String.format("%s %s%n", lines, error.getMessage()));
        }
        return msg.toString();
    }
}
