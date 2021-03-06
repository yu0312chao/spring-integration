/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.json;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.ResolvableType;
import org.springframework.integration.mapping.support.JsonHeaders;
import org.springframework.integration.support.json.JsonObjectMapper;
import org.springframework.integration.support.json.JsonObjectMapperProvider;
import org.springframework.integration.transformer.AbstractTransformer;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Transformer implementation that converts a JSON string payload into an instance of the
 * provided target Class. By default this transformer uses
 * {@linkplain org.springframework.integration.support.json.JsonObjectMapperProvider}
 * factory to get an instance of Jackson 1 or Jackson 2 JSON-processor
 * {@linkplain JsonObjectMapper} implementation depending on the jackson-databind or
 * jackson-mapper-asl libs on the classpath. Any other {@linkplain JsonObjectMapper}
 * implementation can be provided.
 * <p>Since version 3.0, you can omit the target class and the target type can be
 * determined by the {@link JsonHeaders} type entries - including the contents of a
 * one-level container or map type.
 * <p>The type headers can be classes or fully-qualified class names.
 *
 * @author Mark Fisher
 * @author Artem Bilan
 *
 * @see JsonObjectMapper
 * @see org.springframework.integration.support.json.JsonObjectMapperProvider
 *
 * @since 2.0
 */
public class JsonToObjectTransformer extends AbstractTransformer implements BeanClassLoaderAware {

	private final ResolvableType targetType;

	private final JsonObjectMapper<?, ?> jsonObjectMapper;

	private ClassLoader classLoader;

	public JsonToObjectTransformer() {
		this((Class<?>) null);
	}

	public JsonToObjectTransformer(@Nullable Class<?> targetClass) {
		this(ResolvableType.forClass(targetClass));
	}

	/**
	 * Construct an instance based on the provided {@link ResolvableType}.
	 * @param targetType the {@link ResolvableType} to use.
	 * @since 5.2
	 */
	public JsonToObjectTransformer(ResolvableType targetType) {
		this(targetType, null);
	}

	public JsonToObjectTransformer(@Nullable JsonObjectMapper<?, ?> jsonObjectMapper) {
		this((Class<?>) null, jsonObjectMapper);
	}

	public JsonToObjectTransformer(@Nullable Class<?> targetClass, @Nullable JsonObjectMapper<?, ?> jsonObjectMapper) {
		this(ResolvableType.forClass(targetClass), jsonObjectMapper);
	}

	/**
	 * Construct an instance based on the provided {@link ResolvableType} and {@link JsonObjectMapper}.
	 * @param targetType the {@link ResolvableType} to use.
	 * @param jsonObjectMapper  the {@link JsonObjectMapper} to use.
	 * @since 5.2
	 */
	public JsonToObjectTransformer(ResolvableType targetType, @Nullable JsonObjectMapper<?, ?> jsonObjectMapper) {
		Assert.notNull(targetType, "'targetType' must not be null");
		this.targetType = targetType;
		this.jsonObjectMapper = (jsonObjectMapper != null) ? jsonObjectMapper : JsonObjectMapperProvider.newInstance();
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
		if (this.jsonObjectMapper instanceof BeanClassLoaderAware) {
			((BeanClassLoaderAware) this.jsonObjectMapper).setBeanClassLoader(classLoader);
		}
	}

	@Override
	public String getComponentType() {
		return "json-to-object-transformer";
	}

	@Override
	protected Object doTransform(Message<?> message) {
		MessageHeaders headers = message.getHeaders();
		boolean removeHeaders = false;
		ResolvableType valueType = obtainResolvableTypeFromHeadersIfAny(headers);

		if (valueType != null) {
			removeHeaders = true;
		}
		else {
			valueType = this.targetType;
		}

		Object result;
		try {
			result = this.jsonObjectMapper.fromJson(message.getPayload(), valueType);

		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		if (removeHeaders) {
			return getMessageBuilderFactory()
					.withPayload(result)
					.copyHeaders(headers)
					.removeHeaders(JsonHeaders.HEADERS.toArray(new String[0]))
					.build();
		}
		else {
			return result;
		}
	}

	@Nullable
	private ResolvableType obtainResolvableTypeFromHeadersIfAny(MessageHeaders headers) {
		Object valueType = headers.get(JsonHeaders.RESOLVABLE_TYPE);
		Object typeIdHeader = headers.get(JsonHeaders.TYPE_ID);
		if (!(valueType instanceof ResolvableType) && typeIdHeader != null) {
			Class<?> targetClass = getClassForValue(typeIdHeader);
			Class<?> contentClass = null;
			Class<?> keyClass = null;
			Object contentTypeHeader = headers.get(JsonHeaders.CONTENT_TYPE_ID);
			if (contentTypeHeader != null) {
				contentClass = getClassForValue(contentTypeHeader);
			}
			Object keyTypeHeader = headers.get(JsonHeaders.KEY_TYPE_ID);
			if (keyTypeHeader != null) {
				keyClass = getClassForValue(keyTypeHeader);
			}

			valueType = JsonObjectMapper.buildResolvableType(targetClass, contentClass, keyClass);
		}
		return valueType instanceof ResolvableType
				? (ResolvableType) valueType
				: null;
	}

	private Class<?> getClassForValue(Object classValue) {
		if (classValue instanceof Class<?>) {
			return (Class<?>) classValue;
		}
		else {
			try {
				return ClassUtils.forName(classValue.toString(), this.classLoader);
			}
			catch (ClassNotFoundException | LinkageError e) {
				throw new IllegalStateException(e);
			}
		}
	}

}
