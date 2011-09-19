package org.infinispan.cdi.test.interceptor;

import org.infinispan.Cache;
import org.infinispan.cdi.test.interceptor.config.Config;
import org.infinispan.cdi.test.interceptor.config.Custom;
import org.infinispan.cdi.test.interceptor.config.Small;
import org.infinispan.cdi.test.interceptor.service.CacheResultService;
import org.infinispan.cdi.test.interceptor.service.CustomCacheKey;
import org.infinispan.manager.CacheContainer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.cache.interceptor.CacheKey;
import javax.inject.Inject;
import java.lang.reflect.Method;

import static org.infinispan.cdi.test.testutil.Deployments.baseDeployment;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Kevin Pollet - SERLI - (kevin.pollet@serli.com)
 * @see javax.cache.interceptor.CacheResult
 */
@Test(groups = "functional", testName = "cdi.test.interceptor.CacheResultInterceptorTest")
public class CacheResultInterceptorTest extends Arquillian {

   @Deployment
   public static Archive<?> deployment() {
      return baseDeployment()
            .addClass(CacheResultInterceptorTest.class)
            .addPackage(CacheResultService.class.getPackage())
            .addPackage(Config.class.getPackage());
   }

   @Inject
   private CacheContainer cacheContainer;

   @Inject
   private CacheResultService service;

   @Inject
   @Custom
   private Cache<CacheKey, String> customCache;

   @Inject
   @Small
   private Cache<CacheKey, String> smallCache;

   @BeforeMethod
   public void beforeMethod() {
      customCache.clear();
      assertTrue(customCache.isEmpty());
   }

   public void testCacheResult() throws NoSuchMethodException {
      final StringBuilder cacheName = new StringBuilder()
            .append(CacheResultService.class.getName())
            .append(".cacheResult(java.lang.String)");

      final Cache<CacheKey, String> cache = cacheContainer.getCache(cacheName.toString());

      String message = service.cacheResult("Foo");

      assertEquals("Morning Foo", message);
      assertEquals(cache.size(), 1);

      message = service.cacheResult("Foo");

      assertEquals("Morning Foo", message);
      assertEquals(cache.size(), 1);

      assertEquals(service.getCacheResult(), 1);
   }

   public void testCacheResultWithCacheName() {
      String message = service.cacheResultWithCacheName("Pete");

      assertNotNull(message);
      assertEquals("Hi Pete", message);
      assertEquals(customCache.size(), 1);

      message = service.cacheResultWithCacheName("Pete");

      assertNotNull(message);
      assertEquals("Hi Pete", message);
      assertEquals(customCache.size(), 1);

      assertEquals(service.getCacheResultWithCacheName(), 1);
   }

   public void testCacheResultWithCustomCacheKeyGenerator() throws NoSuchMethodException {
      final StringBuilder cacheName = new StringBuilder()
            .append(CacheResultService.class.getName())
            .append(".cacheResultWithCacheKeyGenerator(java.lang.String)");

      final Method method = CacheResultService.class.getMethod("cacheResultWithCacheKeyGenerator", String.class);

      String message = service.cacheResultWithCacheKeyGenerator("Kevin");

      assertEquals("Hello Kevin", message);
      assertEquals(customCache.size(), 1);
      assertTrue(customCache.containsKey(new CustomCacheKey(method, "Kevin")));

      message = service.cacheResultWithCacheKeyGenerator("Kevin");

      assertEquals("Hello Kevin", message);
      assertEquals(customCache.size(), 1);

      assertEquals(service.getCacheResultWithCacheKeyGenerator(), 1);
   }

   public void testCacheResultWithSkipGet() throws NoSuchMethodException {
      String message = service.cacheResultSkipGet("Manik");

      assertNotNull(message);
      assertEquals("Hey Manik", message);
      assertEquals(customCache.size(), 1);

      message = service.cacheResultSkipGet("Manik");

      assertNotNull(message);
      assertEquals("Hey Manik", message);
      assertEquals(customCache.size(), 1);

      assertEquals(service.getCacheResultSkipGet(), 2);
   }

   public void testCacheResultWithSpecificCacheManager() {
      String message = service.cacheResultWithSpecificCacheManager("Pete");

      assertNotNull(message);
      assertEquals("Bonjour Pete", message);
      assertEquals(smallCache.size(), 1);

      message = service.cacheResultWithSpecificCacheManager("Pete");

      assertNotNull(message);
      assertEquals("Bonjour Pete", message);
      assertEquals(smallCache.size(), 1);

      assertEquals(service.getCacheResultWithSpecificCacheManager(), 1);
      assertEquals(smallCache.size(), 1);
      assertEquals(smallCache.getConfiguration().getEvictionMaxEntries(), 4);
   }
}
