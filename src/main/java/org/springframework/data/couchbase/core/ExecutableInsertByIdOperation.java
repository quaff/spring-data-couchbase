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

import com.couchbase.client.java.kv.InsertOptions;
import com.couchbase.client.java.kv.UpsertOptions;
import org.springframework.data.couchbase.core.support.OneAndAllEntity;
import org.springframework.data.couchbase.core.support.WithCollection;

import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import org.springframework.data.couchbase.core.support.WithInsertOptions;
import org.springframework.data.couchbase.core.support.WithScope;
import org.springframework.data.couchbase.core.support.WithUpsertOptions;

public interface ExecutableInsertByIdOperation {

	<T> ExecutableInsertById<T> insertById(Class<T> domainType);

	interface TerminatingInsertById<T> extends OneAndAllEntity<T> {

		@Override
		T one(T object);

		@Override
		Collection<? extends T> all(Collection<? extends T> objects);

	}

	interface InsertByIdWithOptions<T> extends ExecutableInsertByIdOperation.TerminatingInsertById<T>, WithInsertOptions<T> {
		ExecutableInsertByIdOperation.TerminatingInsertById<T> withOptions(InsertOptions options);
	}

	interface InsertByIdWithCollection<T> extends InsertByIdWithOptions<T>, WithCollection<T> {
		InsertByIdWithOptions<T> inCollection(String collection);
	}

	interface InsertByIdWithScope<T> extends InsertByIdWithCollection<T>, WithScope<T> {
		InsertByIdWithCollection<T> inScope(String scope);
	}

	interface InsertByIdWithDurability<T> extends InsertByIdWithScope<T>, WithDurability<T> {

		InsertByIdWithCollection<T> withDurability(DurabilityLevel durabilityLevel);

		InsertByIdWithCollection<T> withDurability(PersistTo persistTo, ReplicateTo replicateTo);

	}

	interface InsertByIdWithExpiry<T> extends InsertByIdWithDurability<T>, WithExpiry<T> {

		@Override
		InsertByIdWithDurability<T> withExpiry(Duration expiry);
	}

	interface ExecutableInsertById<T> extends InsertByIdWithExpiry<T> {}

}
