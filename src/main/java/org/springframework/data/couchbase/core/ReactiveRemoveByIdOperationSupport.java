/*
 * Copyright 2012-2020 the original author or authors
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
package org.springframework.data.couchbase.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

import org.springframework.data.couchbase.core.support.PseudoArgs;
import org.springframework.util.Assert;

import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.RemoveOptions;
import com.couchbase.client.java.kv.ReplicateTo;

public class ReactiveRemoveByIdOperationSupport implements ReactiveRemoveByIdOperation {

	private final ReactiveCouchbaseTemplate template;

	public ReactiveRemoveByIdOperationSupport(final ReactiveCouchbaseTemplate template) {
		this.template = template;
	}

	@Override
	public ReactiveRemoveById removeById() {
		return new ReactiveRemoveByIdSupport(template, null, null, null, PersistTo.NONE, ReplicateTo.NONE,
				DurabilityLevel.NONE, null);
	}

	static class ReactiveRemoveByIdSupport implements ReactiveRemoveById {

		private final ReactiveCouchbaseTemplate template;
		private final String scope;
		private final String collection;
		private final RemoveOptions options;
		private final PersistTo persistTo;
		private final ReplicateTo replicateTo;
		private final DurabilityLevel durabilityLevel;
		private final Long cas;

		ReactiveRemoveByIdSupport(final ReactiveCouchbaseTemplate template, final String scope, final String collection,
				final RemoveOptions options, final PersistTo persistTo, final ReplicateTo replicateTo,
				final DurabilityLevel durabilityLevel, Long cas) {
			this.template = template;
			this.scope = scope;
			this.collection = collection;
			this.options = options;
			this.persistTo = persistTo;
			this.replicateTo = replicateTo;
			this.durabilityLevel = durabilityLevel;
			this.cas = cas;
		}

		@Override
		public Mono<RemoveResult> one(final String id) {
			PseudoArgs pArgs = new PseudoArgs(template, scope, collection);
			return Mono.just(id)
					.flatMap(docId -> template.getCouchbaseClientFactory().withScope(pArgs.getScope())
							.getCollection(pArgs.getCollection()).reactive().remove(id, buildRemoveOptions())
							.map(r -> RemoveResult.from(docId, r)))
					.onErrorMap(throwable -> {
						if (throwable instanceof RuntimeException) {
							return template.potentiallyConvertRuntimeException((RuntimeException) throwable);
						} else {
							return throwable;
						}
					});
		}

		@Override
		public Flux<RemoveResult> all(final Collection<String> ids) {
			return Flux.fromIterable(ids).flatMap(this::one);
		}

		private RemoveOptions buildRemoveOptions() {
			final RemoveOptions options = RemoveOptions.removeOptions();
			if (persistTo != PersistTo.NONE || replicateTo != ReplicateTo.NONE) {
				options.durability(persistTo, replicateTo);
			} else if (durabilityLevel != DurabilityLevel.NONE) {
				options.durability(durabilityLevel);
			}
			if (cas != null) {
				options.cas(cas);
			}
			return options;
		}

		@Override
		public RemoveByIdWithCollection withDurability(final DurabilityLevel durabilityLevel) {
			Assert.notNull(durabilityLevel, "Durability Level must not be null.");
			return new ReactiveRemoveByIdSupport(template, scope, collection, options, persistTo, replicateTo,
					durabilityLevel, cas);
		}

		@Override
		public RemoveByIdWithCollection withDurability(final PersistTo persistTo, final ReplicateTo replicateTo) {
			Assert.notNull(persistTo, "PersistTo must not be null.");
			Assert.notNull(replicateTo, "ReplicateTo must not be null.");
			return new ReactiveRemoveByIdSupport(template, scope, collection, options, persistTo, replicateTo,
					durabilityLevel, cas);
		}

		@Override
		public RemoveByIdWithDurability inCollection(final String collection) {
			return new ReactiveRemoveByIdSupport(template, scope, collection, options, persistTo, replicateTo,
					durabilityLevel, cas);
		}

		@Override
		public RemoveByIdWithCollection inScope(String scope) {
			return new ReactiveRemoveByIdSupport(template, scope, collection, options, persistTo, replicateTo,
					durabilityLevel, cas);
		}

		@Override
		public TerminatingRemoveById withOptions(RemoveOptions options) {
			return new ReactiveRemoveByIdSupport(template, scope, collection, options, persistTo, replicateTo,
					durabilityLevel, cas);
		}

		@Override
		public RemoveByIdWithDurability withCas(Long cas) {
			return new ReactiveRemoveByIdSupport(template, scope, collection, options, persistTo, replicateTo,
					durabilityLevel, cas);
		}
	}

}
