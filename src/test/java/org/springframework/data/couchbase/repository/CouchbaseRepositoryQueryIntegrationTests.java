/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.data.couchbase.repository;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.RemoveResult;
import org.springframework.data.couchbase.domain.Address;
import org.springframework.data.couchbase.domain.Airport;
import org.springframework.data.couchbase.domain.AirportRepository;
import org.springframework.data.couchbase.domain.Person;
import org.springframework.data.couchbase.domain.PersonRepository;
import org.springframework.data.couchbase.domain.User;
import org.springframework.data.couchbase.domain.UserRepository;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.data.couchbase.util.Capabilities;
import org.springframework.data.couchbase.util.ClusterAwareIntegrationTests;
import org.springframework.data.couchbase.util.ClusterType;
import org.springframework.data.couchbase.util.IgnoreWhen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.couchbase.client.core.error.AmbiguousTimeoutException;
import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.error.IndexExistsException;
import com.couchbase.client.core.error.IndexFailureException;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.kv.MutationState;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;

/**
 * Repository tests
 *
 * @author Michael Nitschinger
 * @author Michael Reiche
 * @author Jens Schauder
 */
@SpringJUnitConfig(CouchbaseRepositoryQueryIntegrationTests.Config.class)
@IgnoreWhen(missesCapabilities = Capabilities.QUERY, clusterTypes = ClusterType.MOCKED)
public class CouchbaseRepositoryQueryIntegrationTests extends ClusterAwareIntegrationTests {

	@Autowired CouchbaseClientFactory clientFactory;

	@Autowired AirportRepository airportRepository;

	@Autowired UserRepository userRepository;

	@Autowired CouchbaseTemplate couchbaseTemplate;

	String scopeName = "_default";
	String collectionName = "_default";

	@BeforeEach
	public void beforeEach() {
		try {
			clientFactory.getCluster().queryIndexes().createPrimaryIndex(bucketName());
		} catch (IndexExistsException ex) {
			// ignore, all good.
		}
	}

	@Test
	void shouldSaveAndFindAll() {
		Airport vie = null;
		try {
			vie = new Airport("airports::vie", "vie", "loww");
			airportRepository.save(vie);
			List<Airport> all = new ArrayList<>();
			airportRepository.findAll().forEach(all::add);
			assertFalse(all.isEmpty());
			assertTrue(all.stream().anyMatch(a -> a.getId().equals("airports::vie")));
		} finally {
			airportRepository.delete(vie);
		}
	}

	@Autowired PersonRepository personRepository;

	@Test
	void nestedFind() {
		Person person = null;
		try {
			person = new Person(1, "first", "last");
			Address address = new Address();
			address.setStreet("Maple");
			person.setAddress(address);
			personRepository.save(person);
			List<Person> persons = personRepository.findByAddressStreet("Maple");
			assertEquals(1, persons.size());
			List<Person> persons2 = personRepository.findByMiddlename("Nick");
			assertEquals(1, persons2.size());
		} finally {
			personRepository.deleteById(person.getId().toString());
		}
	}

	@Test
	void annotatedFieldFind() {
		Person person = null;
		try {
			person = new Person(1, "first", "last");
			person.setMiddlename("Nick"); // middlename is stored as nickname
			personRepository.save(person);
			List<Person> persons2 = personRepository.findByMiddlename("Nick");
			assertEquals(1, persons2.size());
		} finally {
			personRepository.deleteById(person.getId().toString());
		}
	}

	// "1\" or name=name or name=\"1")
	@Test
	void findByInjection() {
		Airport vie = null;
		Airport xxx = null;
		try {
			vie = new Airport("airports::vie", "vie", "loww");
			airportRepository.save(vie);
			xxx = new Airport("airports::xxx", "xxx", "xxxx");
			airportRepository.save(xxx);
			List<Airport> airports;
			airports = airportRepository.findAllByIata("1\" or iata=iata or iata=\"1");
			assertEquals(0, airports.size());
			airports = airportRepository.findAllByIata("vie");
			assertEquals(1, airports.size());
		} finally {
			airportRepository.delete(vie);
			airportRepository.delete(xxx);
		}

	}

	@Test
	void findBySimpleProperty() {
		Airport vie = null;
		try {
			vie = new Airport("airports::vie", "vie", "loww");
			vie = airportRepository.save(vie);
			Airport airport2 = airportRepository
					.withOptions(QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.NOT_BOUNDED))
					.findByIata(vie.getIata());
			assertEquals(airport2.getId(), vie.getId());

			List<Airport> airports = airportRepository.findAllByIata("vie");
			assertEquals(1, airports.size());
			Airport airport1 = airportRepository.findById(airports.get(0).getId()).get();
			assertEquals(airport1.getIata(), vie.getIata());
		} finally {
			airportRepository.delete(vie);
		}
	}

	/**
	 * can test against _default._default without setting up additional scope/collection and also test for collections and
	 * scopes that do not exist These same tests should be repeated on non-default scope and collection in a test that
	 * supports collections
	 */
	@Test
	@IgnoreWhen(missesCapabilities = { Capabilities.QUERY, Capabilities.COLLECTIONS }, clusterTypes = ClusterType.MOCKED)
	void findBySimplePropertyWithCollection() {

		Airport vie = new Airport("airports::vie", "vie", "loww");
		try {
			Airport saved = airportRepository.withScope(scopeName).withCollection(collectionName).save(vie);
			// given collection (on scope used by template)
			Airport airport2 = airportRepository.withCollection(collectionName)
					.withOptions(QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS))
					.iata(vie.getIata());
			assertEquals(saved, airport2);

			// given scope and collection

			Airport airport3 = airportRepository.withScope(scopeName).withCollection(collectionName)
					.withOptions(QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS))
					.iata(vie.getIata());
			assertEquals(saved, airport3);

			// given bad collection
			assertThrows(IndexFailureException.class,
					() -> airportRepository.withCollection("bogusCollection").iata(vie.getIata()));

			// given bad scope
			assertThrows(IndexFailureException.class, () -> airportRepository.withScope("bogusScope").iata(vie.getIata()));

		} finally {
			airportRepository.delete(vie);
		}
	}

	@Test
	@IgnoreWhen(hasCapabilities = { Capabilities.COLLECTIONS }, clusterTypes = ClusterType.MOCKED)
	void findBySimplePropertyWithCollectionFail() {
		// can test against _default._default without setting up additional scope/collection
		// the server will throw an exception if it doesn't support COLLECTIONS
		Airport vie = new Airport("airports::vie", "vie", "loww");
		try {

			Airport saved = airportRepository.save(vie);

			assertThrows(CouchbaseException.class, () -> airportRepository.withScope("non_default_scope_name")
					.withCollection(collectionName).iata(vie.getIata()));

		} finally {
			airportRepository.delete(vie);
		}
	}

	@Test
	void findBySimplePropertyWithOptions() {

		Airport vie = new Airport("airports::vie", "vie", "loww");
		JsonArray positionalParams = JsonArray.create().add("this parameter will be overridden");
		// JsonObject namedParams = JsonObject.create().put("$1", vie.getIata());
		try {
			Airport saved = airportRepository.save(vie);
			// Duration of 1 nano-second will cause timeout
			assertThrows(AmbiguousTimeoutException.class, () -> airportRepository
					.withOptions(QueryOptions.queryOptions().timeout(Duration.ofNanos(1))).iata(vie.getIata()));

			Airport airport3 = airportRepository.withOptions(
					QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS).parameters(positionalParams))
					.iata(vie.getIata());
			assertEquals(saved, airport3);

		} finally {
			airportRepository.delete(vie);
		}

		// save() followed by query with NOT_BOUNDED will result in not finding the document
		Airport airport2 = null;
		for (int i = 1; i <= 10; i++) {
			// set version == 0 so save() will be an upsert, not a replace
			Airport saved = airportRepository.save(vie.clearVersion());
			try {
				airport2 = airportRepository.withOptions(
						QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.NOT_BOUNDED).parameters(positionalParams))
						.iata(saved.getIata());
				if (airport2 == null) {
					break;
				}
			} catch (DataRetrievalFailureException drfe) {
				; // was expecting this
			} finally {
				// airportRepository.delete(vie);
				// instead of delete, use removeResult to test QueryOptions.consistentWith()
				RemoveResult removeResult = couchbaseTemplate.removeById().one(vie.getId());
				assertEquals(vie.getId(), removeResult.getId());
				assertTrue(removeResult.getCas() != 0);
				assertTrue(removeResult.getMutationToken().isPresent());
				Airport airport3 = airportRepository
						.withOptions(QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS)
								.consistentWith(MutationState.from(removeResult.getMutationToken().get())))
						.iata(vie.getIata());
				assertNull(airport3, "should have been removed");
			}
		}
		assertNull(airport2, "airport2 should have likely been null at least once");

	}

	@Test
	public void testCas() {
		User user = new User("1", "Dave", "Wilson");
		userRepository.save(user);
		user.setVersion(user.getVersion() - 1);
		assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(user));
		user.setVersion(0);
		userRepository.save(user);
		userRepository.delete(user);
	}

	@Test
	void count() {
		String[] iatas = { "JFK", "IAD", "SFO", "SJC", "SEA", "LAX", "PHX" };

		try {

			airportRepository.saveAll(
					Arrays.stream(iatas).map((iata) -> new Airport("airports::" + iata, iata, iata.toLowerCase(Locale.ROOT)))
							.collect(Collectors.toSet()));

			Long count = airportRepository.countFancyExpression(asList("JFK"), asList("jfk"), false);
			assertEquals(1, count);

			Pageable pageable = PageRequest.of(0, 2);
			Page<Airport> aPage = airportRepository.findAllByIataNot("JFK", pageable);
			assertEquals(iatas.length - 1, aPage.getTotalElements());
			assertEquals(pageable.getPageSize(), aPage.getContent().size());

			long airportCount = airportRepository.count();
			assertEquals(7, airportCount);

			airportCount = airportRepository.countByIataIn("JFK", "IAD", "SFO");
			assertEquals(3, airportCount);

			airportCount = airportRepository.countByIcaoAndIataIn("jfk", "JFK", "IAD", "SFO", "XXX");
			assertEquals(1, airportCount);

			airportCount = airportRepository.countByIcaoOrIataIn("jfk", "LAX", "IAD", "SFO");
			assertEquals(4, airportCount);

			airportCount = airportRepository.countByIataIn("XXX");
			assertEquals(0, airportCount);

		} finally {
			airportRepository
					.deleteAllById(Arrays.stream(iatas).map((iata) -> "airports::" + iata).collect(Collectors.toSet()));
		}
	}

	@Test
	void threadSafeParametersTest() throws Exception {
		String[] iatas = { "JFK", "IAD", "SFO", "SJC", "SEA", "LAX", "PHX" };
		Future[] future = new Future[iatas.length];
		ExecutorService executorService = Executors.newFixedThreadPool(iatas.length);

		try {
			for (int i = 0; i < iatas.length; i++) {
				Airport airport = new Airport("airports::" + iatas[i], iatas[i] /*iata*/,
						iatas[i].toLowerCase(Locale.ROOT) /* lcao */);
				airportRepository.save(airport);
			}

			for (int k = 0; k < 50; k++) {
				Callable<Boolean>[] suppliers = new Callable[iatas.length];
				for (int i = 0; i < iatas.length; i++) {
					final int idx = i;
					suppliers[i] = () -> {
						sleep(iatas.length - idx); // so they are executed out-of-order
						List<Airport> airports = airportRepository.findAllByIata(iatas[idx]);
						String foundName = airportRepository.findAllByIata(iatas[idx]).get(0).getIata();
						assertEquals(iatas[idx], foundName);
						return iatas[idx].equals(foundName);
					};
				}
				for (int i = 0; i < iatas.length; i++) {
					future[i] = executorService.submit(suppliers[i]);
				}
				for (int i = 0; i < iatas.length; i++) {
					future[i].get(); // check is done in Callable
				}
			}

		} finally {
			executorService.shutdown();
			for (int i = 0; i < iatas.length; i++) {
				Airport airport = new Airport("airports::" + iatas[i], iatas[i] /*iata*/, iatas[i] /* lcao */);
				airportRepository.delete(airport);
			}
		}
	}

	@Test
	void threadSafeStringParametersTest() throws Exception {
		String[] iatas = { "JFK", "IAD", "SFO", "SJC", "SEA", "LAX", "PHX" };
		Future[] future = new Future[iatas.length];
		ExecutorService executorService = Executors.newFixedThreadPool(iatas.length);

		try {
			for (int i = 0; i < iatas.length; i++) {
				Airport airport = new Airport("airports::" + iatas[i], iatas[i] /*iata*/, iatas[i].toLowerCase() /* lcao */);
				airportRepository.save(airport);
			}

			for (int k = 0; k < 100; k++) {
				Callable<Boolean>[] suppliers = new Callable[iatas.length];
				for (int i = 0; i < iatas.length; i++) {
					final int idx = i;
					suppliers[i] = () -> {
						sleep(iatas.length - idx); // so they are executed out-of-order
						String foundName = airportRepository.getAllByIata(iatas[idx]).get(0).getIata();
						assertEquals(iatas[idx], foundName);
						return iatas[idx].equals(foundName);
					};
				}
				for (int i = 0; i < iatas.length; i++) {
					future[i] = executorService.submit(suppliers[i]);
				}
				for (int i = 0; i < iatas.length; i++) {
					future[i].get(); // check is done in Callable
				}
			}
		} finally {
			executorService.shutdown();
			for (int i = 0; i < iatas.length; i++) {
				Airport airport = new Airport("airports::" + iatas[i], iatas[i] /*iata*/, iatas[i] /* lcao */);
				airportRepository.delete(airport);
			}
		}
	}

	@Test // DATACOUCH-650
	void deleteAllById() {

		Airport vienna = new Airport("airports::vie", "vie", "LOWW");
		Airport frankfurt = new Airport("airports::fra", "fra", "EDDF");
		Airport losAngeles = new Airport("airports::lax", "lax", "KLAX");

		try {
			airportRepository.saveAll(asList(vienna, frankfurt, losAngeles));

			airportRepository.deleteAllById(asList(vienna.getId(), losAngeles.getId()));

			assertThat(airportRepository.findAll()).containsExactly(frankfurt);
		} finally {
			airportRepository.deleteAll();
		}
	}

	private void sleep(int millis) {
		try {
			Thread.sleep(millis); // so they are executed out-of-order
		} catch (InterruptedException ie) {
			;
		}
	}

	@Configuration
	@EnableCouchbaseRepositories("org.springframework.data.couchbase")
	static class Config extends AbstractCouchbaseConfiguration {

		@Override
		public String getConnectionString() {
			return connectionString();
		}

		@Override
		public String getUserName() {
			return config().adminUsername();
		}

		@Override
		public String getPassword() {
			return config().adminPassword();
		}

		@Override
		public String getBucketName() {
			return bucketName();
		}

		@Override
		public void configureEnvironment(final ClusterEnvironment.Builder builder) {
			builder.ioConfig().maxHttpConnections(11).idleHttpConnectionTimeout(Duration.ofSeconds(4));
			return;
		}

	}
}
