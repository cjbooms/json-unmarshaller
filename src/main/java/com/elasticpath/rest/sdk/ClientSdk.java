package com.elasticpath.rest.sdk;

import static com.google.common.collect.Lists.newArrayList;
import static javax.ws.rs.client.ClientBuilder.newClient;
import static javax.ws.rs.client.Entity.form;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.UriBuilder.fromPath;

import java.lang.reflect.Field;

import javax.ws.rs.core.Form;
import javax.ws.rs.core.UriBuilder;

import com.google.common.base.Joiner;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import com.elasticpath.rest.sdk.annotations.Json;
import com.elasticpath.rest.sdk.annotations.Zoom;
import com.elasticpath.rest.sdk.config.JacksonProvider;
import com.elasticpath.rest.sdk.model.Auth;
import com.elasticpath.rest.sdk.model.AuthToken;

public class ClientSdk {

	public <T> T get(String targetUrl,
					 AuthToken authToken,
					 Class<T> resultClass) {

		if (resultClass.isAnnotationPresent(Zoom.class)) {
			return zoom(targetUrl, authToken, resultClass);
		}

		return httpGet(targetUrl, authToken, resultClass);
	}

	private <T> T zoom(String href,
					   AuthToken authToken,
					   Class<T> resultClass) {
		Iterable<String> zoomSteps = parseZoomSteps(resultClass);

		String targetUrl = buildZoomUrl(href, zoomSteps);

		String jsonResult = httpGet(targetUrl, authToken, String.class);

		return parseZoomResult(resultClass, jsonResult);
	}

	private <T> Iterable<String> parseZoomSteps(Class<T> resultClass) {
		return newArrayList(
				resultClass.getAnnotation(Zoom.class)
						.value()
		);
	}

	private String buildZoomUrl(String href,
								Iterable<String> zoomSteps) {
		return fromPath(href)
				.queryParam("zoom", Joiner.on(":")
						.join(zoomSteps))
				.toString();
	}

	private <T> T parseZoomResult(Class<T> resultClass,
								  String jsonResult) {
		ReadContext jsonContext = JsonPath.parse(jsonResult);
		T resultObject;
		try {
			resultObject = resultClass.newInstance();

			for (Field field : resultClass.getDeclaredFields()) {
				Json annotation = field.getAnnotation(Json.class);
				Object read = jsonContext.read(annotation.value());
				field.set(resultObject, String.valueOf(read));
			}
		} catch (IllegalAccessException | InstantiationException e) {
			throw new IllegalArgumentException(e);
		}
		return resultObject;
	}

	private <T> T httpGet(String targetUrl,
						  AuthToken authToken,
						  Class<T> resultClass) {
		return newClient()
				.register(JacksonProvider.class)
				.target(targetUrl)
				.request(APPLICATION_JSON_TYPE)
				.header(authToken.getHeaderName(), authToken.getHeaderValue())
				.get()
				.readEntity(resultClass);
	}

	public AuthToken auth(UriBuilder targetUrl,
						  Form auth) {

		Auth accessToken = newClient()
				.register(JacksonProvider.class)
				.target(targetUrl)
				.request(APPLICATION_JSON_TYPE)
				.post(form(auth))
				.readEntity(Auth.class);

		return new AuthToken(accessToken.getAccessToken());
	}
}