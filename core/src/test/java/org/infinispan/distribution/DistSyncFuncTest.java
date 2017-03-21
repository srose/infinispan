package org.infinispan.distribution;

import static org.infinispan.test.TestingUtil.extractComponent;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.commons.util.ObjectDuplicator;
import org.infinispan.context.Flag;
import org.infinispan.remoting.transport.Address;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = {"functional", "smoke"}, testName = "distribution.DistSyncFuncTest")
public class DistSyncFuncTest extends BaseDistFunctionalTest<Object, String> {

   public DistSyncFuncTest() {
      testRetVals = true;
   }

   public void testLocationConsensus() {
      String[] keys = new String[100];
      Random r = new Random();
      for (int i = 0; i < 100; i++) keys[i] = Integer.toHexString(r.nextInt());

      for (String key : keys) {
         List<Address> owners = new ArrayList<>();
         for (Cache<Object, String> c : caches) {
            boolean isOwner = isOwner(c, key);
            if (isOwner) owners.add(addressOf(c));
            boolean secondCheck = getCacheTopology(c).getWriteOwners(key).contains(addressOf(c));
            assertTrue(isOwner == secondCheck, "Second check failed for key " + key + " on cache " + addressOf(c) + " isO = " + isOwner + " sC = " + secondCheck);
         }
         // check consensus
         assertOwnershipConsensus(key);
         assertEquals(2, owners.size(),"Expected 2 owners for key " + key + " but was " + owners);
      }
   }

   private void assertOwnershipConsensus(String key) {
      List l1 = getCacheTopology(c1).getDistribution(key).writeOwners();
      List l2 = getCacheTopology(c2).getDistribution(key).writeOwners();
      List l3 = getCacheTopology(c3).getDistribution(key).writeOwners();
      List l4 = getCacheTopology(c4).getDistribution(key).writeOwners();

      assertEquals(l1, l2, "L1 "+l1+" and L2 "+l2+" don't agree.");
      assertEquals(l2, l3, "L2 "+l2+" and L3 "+l3+" don't agree.");
      assertEquals(l3, l4, "L3 "+l3+" and L4 "+l4+" don't agree.");

   }

   public void testBasicDistribution() throws Throwable {
      for (Cache<Object, String> c : caches)
         assertTrue(c.isEmpty());

      final Object k1 = getKeyForCache(caches.get(1));
      getOwners(k1)[0].put(k1, "value");

      // No non-owners have requested the key, so no invalidations
      asyncWait(k1, PutKeyValueCommand.class);

      for (Cache<Object, String> c : caches) {
         if (isOwner(c, k1)) {
            assertIsInContainerImmortal(c, k1);
         } else {
            assertIsNotInL1(c, k1);
         }
      }

      // should be available everywhere!
      assertOnAllCachesAndOwnership(k1, "value");

      // and should now be in L1

      for (Cache<Object, String> c : caches) {
         if (isOwner(c, k1)) {
            assertIsInContainerImmortal(c, k1);
         } else {
            assertIsInL1(c, k1);
         }
      }
   }

   public void testPutFromNonOwner() {
      initAndTest();
      Cache<Object, String> nonOwner = getFirstNonOwner("k1");

      Object retval = nonOwner.put("k1", "value2");
      asyncWait("k1", PutKeyValueCommand.class, getSecondNonOwner("k1"));

      if (testRetVals) assert "value".equals(retval);
      assertOnAllCachesAndOwnership("k1", "value2");
   }

   public void testPutIfAbsentFromNonOwner() {
      initAndTest();
      log.trace("Here it begins");
      Object retval = getFirstNonOwner("k1").putIfAbsent("k1", "value2");

      if (testRetVals) assertEquals("value", retval);

      assertOnAllCachesAndOwnership("k1", "value");

      c1.clear();
      asyncWait(null, ClearCommand.class);

      retval = getFirstNonOwner("k1").putIfAbsent("k1", "value2");

      eventually(() -> {
         try {
            assertOnAllCachesAndOwnership("k1", "value2");
         } catch (AssertionError e) {
            log.debugf("Assertion failed once", e);
            return false;
         }
         return true;
      });

      if (testRetVals) assertNull(retval);
   }

   public void testRemoveFromNonOwner() {
      initAndTest();
      Object retval = getFirstNonOwner("k1").remove("k1");
      asyncWait("k1", RemoveCommand.class, getSecondNonOwner("k1"));
      if (testRetVals) assertEquals("value", retval);

      assertRemovedOnAllCaches("k1");
   }

   public void testConditionalRemoveFromNonOwner() {
      initAndTest();
      log.trace("Here we start");
      boolean retval = getFirstNonOwner("k1").remove("k1", "value2");
      if (testRetVals) assertFalse(retval,"Should not have removed entry");

      assertOnAllCachesAndOwnership("k1", "value");

      assertEquals("value", caches.get(1).get("k1"));

      Cache<Object, String> owner = getFirstNonOwner("k1");

      retval = owner.remove("k1", "value");
      asyncWait("k1", RemoveCommand.class, getSecondNonOwner("k1"));
      if (testRetVals) assertTrue(retval, "Should have removed entry");

      assertNull(caches.get(1).get("k1"), "expected null but received " + caches.get(1).get("k1"));
      assertRemovedOnAllCaches("k1");
   }

   public void testReplaceFromNonOwner() {
      initAndTest();
      Object retval = getFirstNonOwner("k1").replace("k1", "value2");
      if (testRetVals) assertEquals("value", retval);

      asyncWait("k1", ReplaceCommand.class, getSecondNonOwner("k1"));

      assertOnAllCachesAndOwnership("k1", "value2");

      c1.clear();
      asyncWait(null, ClearCommand.class);

      retval = getFirstNonOwner("k1").replace("k1", "value2");
      if (testRetVals) assertNull(retval);

      assertRemovedOnAllCaches("k1");
   }

   public void testConditionalReplaceFromNonOwner() {
      initAndTest();
      Cache<Object, String> nonOwner = getFirstNonOwner("k1");
      boolean retval = nonOwner.replace("k1", "valueX", "value2");
      if (testRetVals) assertFalse(retval, "Should not have replaced");

      assertOnAllCachesAndOwnership("k1", "value");

      assertFalse(extractComponent(nonOwner, DistributionManager.class).getCacheTopology().isWriteOwner("k1"));
      retval = nonOwner.replace("k1", "value", "value2");
      asyncWait("k1", ReplaceCommand.class, getSecondNonOwner("k1"));
      if (testRetVals) assertTrue(retval,"Should have replaced");

      assertOnAllCachesAndOwnership("k1", "value2");
   }

   public void testClear() throws InterruptedException {
      for (Cache<Object, String> c : caches)
         assertTrue(c.isEmpty());

      for (int i = 0; i < 10; i++) {
         getOwners("k" + i)[0].put("k" + i, "value" + i);
         // There will be no caches to invalidate as this is the first command of the test
         asyncWait("k" + i, PutKeyValueCommand.class);
         assertOnAllCachesAndOwnership("k" + i, "value" + i);
      }

      // this will fill up L1 as well
      for (int i = 0; i < 10; i++) assertOnAllCachesAndOwnership("k" + i, "value" + i);

      for (Cache<Object, String> c : caches)
         assertFalse(c.isEmpty());

      c1.clear();
      asyncWait(null, ClearCommand.class);

      for (Cache<Object, String> c : caches)
         assertTrue(c.isEmpty());
   }

   public void testKeyValueEntryCollections() {
      c1.put("1", "one");
      asyncWait("1", PutKeyValueCommand.class);
      c2.put("2", "two");
      asyncWait("2", PutKeyValueCommand.class);
      c3.put("3", "three");
      asyncWait("3", PutKeyValueCommand.class);
      c4.put("4", "four");
      asyncWait("4", PutKeyValueCommand.class);

      for (Cache c : caches) {
         Set expKeys = TestingUtil.getInternalKeys(c);
         Collection expValues = TestingUtil.getInternalValues(c);

         Set expKeyEntries = ObjectDuplicator.duplicateSet(expKeys);
         Collection expValueEntries = ObjectDuplicator.duplicateCollection(expValues);

         Set keys = c.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).keySet();
         for (Object key : keys)
            assertTrue(expKeys.remove(key));
         assertTrue(expKeys.isEmpty(), "Did not see keys " + expKeys + " in iterator!");

         Collection values = c.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).values();
         for (Object value : values)
            assertTrue(expValues.remove(value));
         assertTrue(expValues.isEmpty(), "Did not see keys " + expValues + " in iterator!");

         Set<Map.Entry> entries = c.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).entrySet();
         for (Map.Entry entry : entries) {
            assertTrue(expKeyEntries.remove(entry.getKey()));
            assertTrue(expValueEntries.remove(entry.getValue()));
         }
         assertTrue(expKeyEntries.isEmpty(), "Did not see keys " + expKeyEntries + " in iterator!");
         assertTrue(expValueEntries.isEmpty(), "Did not see keys " + expValueEntries + " in iterator!");
      }
   }
}
