/*
 * Copyright 2021 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.couchbase.core.support;

import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.data.couchbase.core.query.Query;

public class PseudoArgs<OPTS> {
	OPTS options;
	String scopeName;
	String collectionName;

	public PseudoArgs(OPTS options, String scopeName, String collectionName) {
		this.options = options;
		this.scopeName = scopeName;
		this.collectionName = collectionName;
	}

	public PseudoArgs(ReactiveCouchbaseTemplate template, String scope, String collection) {
		this(template, scope, collection, null, null);
	}

	public PseudoArgs(ReactiveCouchbaseTemplate template, String scope, String collection, OPTS options) {
		this(template, scope, collection, options, null);
	}

	/**
	 * return scope, collection and options in following precedence <br>
	 * 1) values from fluent api<br>
	 * 2) values from dynamic proxy (via template threadLocal)<br>
	 * 3) the values from the couchbaseClientFactory<br>
	 * 
	 * @param template
	 * @param scope
	 * @param collection
	 * @param options
	 * @param query
	 */
	public PseudoArgs(ReactiveCouchbaseTemplate template, String scope, String collection, OPTS options, Query query) {

		// 1) values from the args (fluent api)

		String scopeForQuery = scope;
		String collectionForQuery = collection;
		OPTS optionsForQuery = options;

		// 2) from DynamicProxy via template threadLocal

		scopeForQuery = scopeForQuery != null ? scopeForQuery : getTlScopeName(template);
		collectionForQuery = collectionForQuery != null ? collectionForQuery : getTlCollectionName(template);
		optionsForQuery = optionsForQuery != null ? optionsForQuery : getTlOptions(template);

		// if a collection was specified but no scope, use the scope from the clientFactory

		if (collectionForQuery != null && scopeForQuery == null) {
			scopeForQuery = template.getCouchbaseClientFactory().getScope().name();
		}

		// specifying scope and collection = _default is not necessary and will fail if server doesn't have collections

		if ((scopeForQuery == null || "_default".equals(scopeForQuery))
				&& (collectionForQuery == null || "_default".equals(collectionForQuery))) {
			scopeForQuery = null;
			collectionForQuery = null;
		}

		this.scopeName = scopeForQuery;
		this.collectionName = collectionForQuery;
		this.options = optionsForQuery;
	}

	/**
	 * @@return the options
	 */
	public OPTS getOptions() {
		return this.options;
	}

	/**
	 * @@return the scope name
	 */
	public String getScope() {
		return this.scopeName;
	}

	/**
	 * @@return the collection name
	 */
	public String getCollection() {
		return this.collectionName;
	}

	/**
	 * @@return the options from the ThreadLocal field of the template
	 */
	private OPTS getTlOptions(ReactiveCouchbaseTemplate template) {
		return template.getThrdLocalArgs() == null ? null : (OPTS) (template.getThrdLocalArgs().getOptions());
	}

	/**
	 * @@return the scope name from the ThreadLocal field of the template
	 */
	private String getTlScopeName(ReactiveCouchbaseTemplate template) {
		return template.getThrdLocalArgs() == null ? null : template.getThrdLocalArgs().getScope();
	}

	/**
	 * @@return the collection name from the ThreadLocal field of the template
	 */
	private String getTlCollectionName(ReactiveCouchbaseTemplate template) {
		return template.getThrdLocalArgs() == null ? null : template.getThrdLocalArgs().getCollection();
	}
}
