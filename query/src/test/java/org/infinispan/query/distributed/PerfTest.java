package org.infinispan.query.distributed;

import static org.testng.AssertJUnit.assertEquals;

import java.util.concurrent.TimeUnit;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.util.Util;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.queries.faceting.Car;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

/**
 * Stress test running on an indexed Cache which is storing the index in a distributed Lucene Directory
 *
 * This test takes approximately 30 seconds: better increment the number of iterations.
 * It takes approximately half a second to insert 2000 entries in the indexed cache
 * when running DIST in 4 nodes, and it's much faster when run in single node.
 *
 * TestNG enables assertions: these have an impact on Lucene so better run it as a main!
 *
 * @author Adrian Nistor (C) 2013 Red Hat Inc.
 * @author Sanne Grinovero (C) 2013 Red Hat Inc.
 */
@Test(groups = "profiling", testName = "query.distributed.PerfTest", singleThreaded = true)
public class PerfTest extends MultipleCacheManagersTest {

   private static final int NUM_NODES = 4;
   private static final int LOG_ON_EACH = 2000;
   private static final int NUMBER_OF_ITERATIONS = 50;

   @Override
   protected void createCacheManagers() throws Throwable {
      for (int i = 0; i < NUM_NODES; i++) {
         EmbeddedCacheManager cacheManager = TestCacheManagerFactory.fromXml("indexing-perf.xml");
         registerCacheManager(cacheManager);
      }
      waitForClusterToForm(new String[]{
            getDefaultCacheName(),
            "LuceneIndexesMetadata",
            "LuceneIndexesData",
            "LuceneIndexesLocking",
      });
   }

   public void testIndexing() throws Exception {
      int carId = 0;
      int cacheId = 0;
      final long start = System.nanoTime();
      for (int outherLoop = 0; outherLoop < NUMBER_OF_ITERATIONS; outherLoop++) {
         final Cache<String, Car> cache = getWriteOnlyCache(cacheId++ % NUM_NODES);
         System.out.print("Using " + cacheId + ": " + cache + "\t");
         final long blockStart = System.nanoTime();
         cache.startBatch();
         for (int innerLoop = 0; innerLoop < LOG_ON_EACH; innerLoop++) {
            carId++;
            cache.put("car" + carId, new Car("megane", "blue", 300 + carId));
            carId++;
            cache.put("car" + carId, new Car("bmw", "blue", 300 + carId));
         }
         cache.endBatch(true);
         System.out.println("Inserted " + LOG_ON_EACH + " cars in " + Util.prettyPrintTime(System.nanoTime() - blockStart, TimeUnit.NANOSECONDS));
      }
      System.out.println("Test took " + Util.prettyPrintTime(System.nanoTime() - start, TimeUnit.NANOSECONDS));

      verifyFindsCar(carId / 2, "megane");
   }

   private Cache<String, Car> getWriteOnlyCache(int cacheId) {
      Cache<String, Car> cache = cache(cacheId);
      AdvancedCache<String, Car> advancedCache = cache.getAdvancedCache();
      AdvancedCache<String, Car> withFlags = advancedCache.withFlags(Flag.IGNORE_RETURN_VALUES, Flag.SKIP_INDEX_CLEANUP);
      return withFlags;
   }

   private void verifyFindsCar(int expectedCount, String carMake) {
      for (int i = 0; i < NUM_NODES; i++) {
         verifyFindsCar(cache(i), expectedCount, carMake);
      }
   }

   private void verifyFindsCar(Cache cache, int expectedCount, String carMake) {
      String q = String.format("FROM %s WHERE make:'%s'", Car.class.getName(), carMake);
      Query cacheQuery = Search.getQueryFactory(cache).create(q);
      assertEquals(expectedCount, cacheQuery.execute().count().value());
   }

   /**
    * Recommended tuning options:
    * -Xms4g -Xmx4g -XX:+UseParallelGC -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -XX:+UseLargePages -Djava.awt.headless=true -Dlog4j.configurationFile=log4j2.xml
    *
    * Diagnostic options:
    * -Xms6g -Xmx6g -XX:+UseParallelGC -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -XX:+UseLargePages -Djava.awt.headless=true -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:-PrintTLAB -XX:+PrintHeapAtGCExtended -XX:+PrintAdaptiveSizePolicy -XX:+PrintGCApplicationStoppedTime -XX:-PrintGCApplicationConcurrentTime -Xloggc:/tmp/lucene.gclog -XX:+ParallelRefProcEnabled -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=delay=10s,duration=24h,filename=/tmp/flight_record_lucene.jfr,settings=/opt/flightrecorder/profile_2ms -Dlog4j.configurationFile=log4j2.xml
    */
   public static void main(String[] args) throws Throwable {
      PerfTest test = new PerfTest();
      test.createBeforeClass();
      try {
         test.testIndexing();
      } finally {
         test.destroy();
      }
   }

}
