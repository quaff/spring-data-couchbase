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

import java.time.Duration;
import java.util.Collection;

import org.springframework.data.couchbase.core.support.OneAndAllEntity;
import org.springframework.data.couchbase.core.support.WithCollection;
import org.springframework.data.couchbase.core.support.WithReplaceOptions;
import org.springframework.data.couchbase.core.support.WithScope;

import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplaceOptions;
import com.couchbase.client.java.kv.ReplicateTo;

public interface ExecutableReplaceByIdOperation {

	<T> ExecutableReplaceById<T> replaceById(Class<T> domainType);

	interface TerminatingReplaceById<T> extends OneAndAllEntity<T> {

		@Override
		T one(T object);

		@Override
		Collection<? extends T> all(Collection<? extends T> objects);

	}

	interface ReplaceByIdWithOptions<T> extends TerminatingReplaceById<T>, WithReplaceOptions<T> {
		TerminatingReplaceById<T> withOptions(ReplaceOptions options);
	}

	interface ReplaceByIdWithCollection<T> extends ReplaceByIdWithOptions<T>, WithCollection<T> {
		ReplaceByIdWithOptions<T> inCollection(String collection);
	}

	interface ReplaceByIdWithScope<T> extends ReplaceByIdWithCollection<T>, WithScope<T> {
		ReplaceByIdWithCollection<T> inScope(String scope);
	}

	interface ReplaceByIdWithDurability<T> extends ReplaceByIdWithScope<T>, WithDurability<T> {

		ReplaceByIdWithScope<T> withDurability(DurabilityLevel durabilityLevel);

		ReplaceByIdWithScope<T> withDurability(PersistTo persistTo, ReplicateTo replicateTo);

	}

	interface ReplaceByIdWithExpiry<T> extends ReplaceByIdWithDurability<T>, WithExpiry<T> {
		ReplaceByIdWithDurability<T> withExpiry(final Duration expiry);
	}

	interface ExecutableReplaceById<T> extends ReplaceByIdWithExpiry<T> {}

}
