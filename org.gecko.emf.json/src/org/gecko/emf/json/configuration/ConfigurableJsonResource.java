/**
 * Copyright (c) 2012 - 2022 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.json.configuration;

import static org.eclipse.emfcloud.jackson.databind.EMFContext.Attributes.RESOURCE;
import static org.eclipse.emfcloud.jackson.databind.EMFContext.Attributes.RESOURCE_SET;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emfcloud.jackson.annotations.EcoreIdentityInfo;
import org.eclipse.emfcloud.jackson.annotations.EcoreReferenceInfo;
import org.eclipse.emfcloud.jackson.annotations.EcoreTypeInfo;
import org.eclipse.emfcloud.jackson.databind.EMFContext;
import org.eclipse.emfcloud.jackson.handlers.URIHandler;
import org.eclipse.emfcloud.jackson.module.EMFModule;
import org.eclipse.emfcloud.jackson.module.EMFModule.Feature;
import org.eclipse.emfcloud.jackson.resource.JsonResource;
import org.gecko.emf.json.constants.EMFJs;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;

/**
 * 
 * @author jalbert
 * @since 27 Jun 2018
 */
public class ConfigurableJsonResource extends JsonResource {
	
	private final ObjectMapper srcMapper;

	/**
	 * Creates a new instance.
	 */
	public ConfigurableJsonResource(URI uri) {
		super(uri, null);
		srcMapper = null;
	}
	
	public ConfigurableJsonResource(URI uri, ObjectMapper mapper) {
		super(uri, mapper);
		this.srcMapper = mapper;
	}
	
	public ObjectMapper configureMapper(Map<?,?> options) {
		boolean isNew = srcMapper == null;
		final ObjectMapper mapper = isNew ? new ObjectMapper() : srcMapper.copy();
		
		mapper.configure(MapperFeature.IGNORE_DUPLICATE_MODULE_REGISTRATIONS, false);
		
		/*
		 * Setting mapper options
		 */
		String timeFormat = getOrDefault(options, EMFJs.OPTION_DATE_FORMAT, isNew ? "yyyy-MM-dd'T'HH:mm:ss'Z'" : null);
		if (timeFormat != null) {
			final SimpleDateFormat dateFormat = new SimpleDateFormat(timeFormat, Locale.ENGLISH);
			dateFormat.setTimeZone(TimeZone.getDefault());
			mapper.setDateFormat(dateFormat);
		}
		
		/*
		 * Add a problem handler
		 */
		Object problemHandler = getOrDefault(options, EMFJs.OPTIONS_PROBLEM_HANDLER, null);
		if (problemHandler instanceof DeserializationProblemHandler) {
			mapper.addHandler((DeserializationProblemHandler) problemHandler);
		}
		
		Boolean indentOutput = getOrDefault(options, EMFJs.OPTION_INDENT_OUTPUT, isNew ? true : null);
		if (indentOutput != null) {
			mapper.configure(SerializationFeature.INDENT_OUTPUT, indentOutput);
		}
		
		/*
		 * Setting module options
		 */
		EMFModule module = new EMFModule();
		Boolean serContainment = getOrDefault(options, EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF, isNew ? false : null);
		if (serContainment != null) {
			module.configure(Feature.OPTION_SERIALIZE_CONTAINMENT_AS_HREF, serContainment);
		}
		Boolean serDefaults = getOrDefault(options, EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE, isNew ? false : null);
		if (serDefaults != null) {
			module.configure(Feature.OPTION_SERIALIZE_DEFAULT_VALUE, serDefaults);
		}
		Boolean serTypes = getOrDefault(options, EMFJs.OPTION_SERIALIZE_TYPE, isNew ? true : null);
		if (serTypes != null) {
			module.configure(Feature.OPTION_SERIALIZE_TYPE, serTypes);
		}
		Boolean useId = getOrDefault(options, EMFJs.OPTION_USE_ID, isNew ? false : null);
		if (useId != null) {
			module.configure(Feature.OPTION_USE_ID, useId);
		}
		
		Object uriHandlerObject = options.get(XMLResource.OPTION_URI_HANDLER);
		
		URIHandler uriHandler = null;
		if(uriHandlerObject != null) {
			if(uriHandlerObject instanceof XMLResource.URIHandler) {
				uriHandler = new XMLResourceUriHandlerWrapper((XMLResource.URIHandler) uriHandlerObject);
			} else {
				uriHandler = (URIHandler) uriHandlerObject; 
			}
			module.setUriHandler(uriHandler);
		}
		String refField = getOrDefault(options, EMFJs.OPTION_REF_FIELD, null);
		String idField = getOrDefault(options, EMFJs.OPTION_ID_FIELD, null);
		String typeField = getOrDefault(options, EMFJs.OPTION_TYPE_FIELD, null);
		
		if(refField != null && uriHandler != null) {
			module.setReferenceInfo(new EcoreReferenceInfo(refField, uriHandler));
		} else if(refField != null && uriHandler == null) {
			module.setReferenceInfo(new EcoreReferenceInfo(refField));
		} else if (refField == null && uriHandler != null) {
			module.setReferenceInfo(new EcoreReferenceInfo(uriHandler));
		}
		
		if(typeField != null) {
			module.setTypeInfo(new EcoreTypeInfo(typeField));
		}
		
		if(idField != null) {
			module.setIdentityInfo(new EcoreIdentityInfo(idField));
		}
		mapper.registerModule(module);
		
		return mapper;
	}
	
	/**
	 * @param options
	 * @param optionDateFormat
	 * @param string
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> T getOrDefault(Map<?, ?> options, String key, T defaultvalue) {
		Object value = options.get(key);
		if(value == null) {
			return defaultvalue;
		}
		return (T) value;
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		if (options == null) {
			options = Collections.<String, Object>emptyMap();
		}

		if (inputStream instanceof URIConverter.Loadable) {

			((URIConverter.Loadable) inputStream).loadResource(this);

		} else {

			ContextAttributes attributes = EMFContext
					.from(options)
					.withPerCallAttribute(RESOURCE_SET, getResourceSet())
					.withPerCallAttribute(RESOURCE, this);

			
			configureMapper(options).reader()
					.with(attributes)
					.forType(Resource.class)
					.withValueToUpdate(this)
					.readValue(inputStream);

		}
	}

	@Override
	protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
		if (options == null) {
			options = Collections.<String, Object>emptyMap();
		}

		if (outputStream instanceof URIConverter.Saveable) {

			((URIConverter.Saveable) outputStream).saveResource(this);

		} else {

			configureMapper(options).writer()
					.with(EMFContext.from(options))
					.writeValue(outputStream, this);

		}
	}
	
}
