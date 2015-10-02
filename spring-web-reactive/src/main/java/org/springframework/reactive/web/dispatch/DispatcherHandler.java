/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.reactive.web.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.rx.Streams;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.reactive.web.http.HttpHandler;
import org.springframework.reactive.web.http.ServerHttpRequest;
import org.springframework.reactive.web.http.ServerHttpResponse;

/**
 * @author Rossen Stoyanchev
 */
public class DispatcherHandler implements HttpHandler, ApplicationContextAware {

	private static final Log logger = LogFactory.getLog(DispatcherHandler.class);


	private List<HandlerMapping> handlerMappings;

	private List<HandlerAdapter> handlerAdapters;

	private List<HandlerResultHandler> resultHandlers;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		initStrategies(applicationContext);
	}

	protected void initStrategies(ApplicationContext context) {

		Map<String, HandlerMapping> mappingBeans =
				BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);

		this.handlerMappings = new ArrayList<>(mappingBeans.values());
		AnnotationAwareOrderComparator.sort(this.handlerMappings);

		Map<String, HandlerAdapter> adapterBeans =
				BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);

		this.handlerAdapters = new ArrayList<>(adapterBeans.values());
		AnnotationAwareOrderComparator.sort(this.handlerAdapters);

		Map<String, HandlerResultHandler> beans =
				BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerResultHandler.class, true, false);

		this.resultHandlers = new ArrayList<>(beans.values());
		AnnotationAwareOrderComparator.sort(this.resultHandlers);
	}


	@Override
	public Publisher<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {

		if (logger.isDebugEnabled()) {
			logger.debug("Processing " + request.getMethod() + " request for [" + request.getURI() + "]");
		}

		Object handler = getHandler(request);
		if (handler == null) {
			// No exception handling mechanism yet
			response.setStatusCode(HttpStatus.NOT_FOUND);
			return Streams.empty();
		}

		HandlerAdapter handlerAdapter = getHandlerAdapter(handler);

		try {
			HandlerResult result = handlerAdapter.handle(request, response, handler);
			for (HandlerResultHandler resultHandler : resultHandlers) {
				if (resultHandler.supports(result)) {
					return resultHandler.handleResult(request, response, result);
				}
			}
			return Streams.fail(new IllegalStateException(
					"No HandlerResultHandler for " + result.getValue()));
		}
		catch(Exception ex) {
			return Streams.fail(ex);
		}

	}

	protected Object getHandler(ServerHttpRequest request) {
		Object handler = null;
		for (HandlerMapping handlerMapping : this.handlerMappings) {
			handler = handlerMapping.getHandler(request);
			if (handler != null) {
				break;
			}
		}
		return handler;
	}

	protected HandlerAdapter getHandlerAdapter(Object handler) {
		for (HandlerAdapter handlerAdapter : this.handlerAdapters) {
			if (handlerAdapter.supports(handler)) {
				return handlerAdapter;
			}
		}
		// more specific exception
		throw new IllegalStateException("No HandlerAdapter for " + handler);
	}

}
