/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.llama.am.impl;

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestReservation;
import com.cloudera.llama.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TestGangAntiDeadlockLlamaAM {

  @Test
  public void testBackedOffReservation() {
    PlacedReservation pr = new PlacedReservationImpl(UUID.randomUUID(),
        createReservation(UUID.randomUUID(), 1, true));
    long now = System.currentTimeMillis();
    GangAntiDeadlockLlamaAM.BackedOffReservation br1 =
        new GangAntiDeadlockLlamaAM.BackedOffReservation(pr, 0);
    Assert.assertEquals(pr, br1.getReservation());
    Assert.assertTrue(System.currentTimeMillis() - now >=
        br1.getDelay(TimeUnit.MILLISECONDS));
    GangAntiDeadlockLlamaAM.BackedOffReservation br2 =
        new GangAntiDeadlockLlamaAM.BackedOffReservation(pr, 100);
    Assert.assertTrue(System.currentTimeMillis() - now + 100 >=
        br1.getDelay(TimeUnit.MILLISECONDS));
    Assert.assertTrue(br1.compareTo(br2) < 0);
  }

  private static Set<String> EXPECTED = new HashSet<String>();

  static {
    EXPECTED.add("start");
    EXPECTED.add("stop");
    EXPECTED.add("isRunning");
    EXPECTED.add("getNodes");
    EXPECTED.add("reserve");
    EXPECTED.add("getReservation");
    EXPECTED.add("releaseReservation");
    EXPECTED.add("releaseReservationsForClientId");
    EXPECTED.add("addListener");
    EXPECTED.add("removeListener");
    EXPECTED.add("releaseReservationsForQueue");
  }

  public class MyGangAntiDeadlockLlamaAM extends GangAntiDeadlockLlamaAM {
    public MyGangAntiDeadlockLlamaAM(Configuration conf, LlamaAM llamaAM) {
      super(conf, llamaAM);
    }

    @Override void startDeadlockResolverThread() {
    }

  }

  public class MyLlamaAM extends LlamaAMImpl {
    private boolean running;
    Set<String> invoked;
    Map<UUID, PlacedReservationImpl> reservations;

    protected MyLlamaAM(Configuration conf) {
      super(conf);
      invoked = new HashSet<String>();
      reservations = new HashMap<UUID, PlacedReservationImpl>();
    }

    @Override
    public void start() throws LlamaAMException {
      running = true;
      invoked.add("start");
    }

    @Override
    public void stop() {
      running = false;
      invoked.add("stop");
    }

    @Override
    public boolean isRunning() {
      invoked.add("isRunning");
      return running;
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      invoked.add("getNodes");
      return null;
    }

    @Override
    public PlacedReservation reserve(UUID reservationId,
        Reservation reservation)
        throws LlamaAMException {
      invoked.add("reserve");
      PlacedReservationImpl pr = new PlacedReservationImpl(reservationId,
          reservation);
      reservations.put(reservationId, pr);
      return pr;
    }

    @Override
    public PlacedReservation getReservation(UUID reservationId)
        throws LlamaAMException {
      invoked.add("getReservation");
      return reservations.get(reservationId);
    }

    @Override
    public PlacedReservation releaseReservation(UUID handle, UUID reservationId)
        throws LlamaAMException {
      invoked.add("releaseReservation");
      PlacedReservationImpl r = reservations.remove(reservationId);
      if (r != null) {
        r.setStatus(PlacedReservation.Status.ENDED);
      }
      return r;
    }

    @Override
    public List<PlacedReservation> releaseReservationsForHandle(UUID handle)
        throws LlamaAMException {
      invoked.add("releaseReservationsForClientId");
      List<PlacedReservation> list = new ArrayList<PlacedReservation>();
      Iterator<Map.Entry<UUID, PlacedReservationImpl>> it =
          reservations.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<UUID, PlacedReservationImpl> entry = it.next();
        if (entry.getValue().getHandle().equals(handle)) {
          it.remove();
          entry.getValue().setStatus(PlacedReservation.Status.ENDED);
          list.add(entry.getValue());
        }
      }
      return list;
    }

    @Override
    public List<PlacedReservation> releaseReservationsForQueue(String queue)
        throws LlamaAMException {
      invoked.add("releaseReservationsForQueue");
      List<PlacedReservation> list = new ArrayList<PlacedReservation>();
      Iterator<Map.Entry<UUID, PlacedReservationImpl>> it =
          reservations.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<UUID, PlacedReservationImpl> entry = it.next();
        if (entry.getValue().getQueue().equals(queue)) {
          it.remove();
          entry.getValue().setStatus(PlacedReservation.Status.ENDED);
          list.add(entry.getValue());
        }
      }
      return list;
    }

    @Override
    public void addListener(LlamaAMListener listener) {
      invoked.add("addListener");
      super.addListener(listener);
    }

    @Override
    public void removeListener(LlamaAMListener listener) {
      invoked.add("removeListener");
      super.removeListener(listener);
    }

    @Override
    public void dispatch(LlamaAMEvent event) {
      super.dispatch(event);
    }
  }

  private Reservation createReservation(UUID handle, int resources,
      boolean gang) {
    List<Resource> list = new ArrayList<Resource>();
    for (int i = 0; i < resources; i++) {
      list.add(TestReservation.createResource());
    }
    return new Reservation(handle, "q", list, gang);
  }

  private static final long NO_ALLOCATION_LIMIT = 50;
  private static final long BACKOFF_PERCENT = 50;
  private static final long BACKOFF_MIN_DELAY = 100;
  private static final long BACKOFF_MAX_DELAY = 150;

  private Configuration createGangConfig() {
    Configuration conf = new Configuration(false);
    conf.setLong(LlamaAM.GANG_ANTI_DEADLOCK_NO_ALLOCATION_LIMIT_KEY,
        NO_ALLOCATION_LIMIT);
    conf.setLong(LlamaAM.GANG_ANTI_DEADLOCK_BACKOFF_PERCENT_KEY,
        BACKOFF_PERCENT);
    conf.setLong(LlamaAM.GANG_ANTI_DEADLOCK_BACKOFF_MIN_DELAY_KEY,
        BACKOFF_MIN_DELAY);
    conf.setLong(LlamaAM.GANG_ANTI_DEADLOCK_BACKOFF_MAX_DELAY_KEY,
        BACKOFF_MAX_DELAY);
    return conf;
  }

  @SuppressWarnings("unchecked")
  private void testDelegate(boolean gang) throws Exception {
    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new GangAntiDeadlockLlamaAM(conf, am);

    Assert.assertFalse(gAm.isRunning());
    gAm.start();
    Assert.assertTrue(gAm.isRunning());
    gAm.getNodes();
    gAm.addListener(null);
    gAm.removeListener(null);
    UUID handle = UUID.randomUUID();
    Reservation<Resource> reservation = createReservation(handle, 1,
        gang);
    Assert.assertTrue(gAm.localReservations.isEmpty());
    Assert.assertTrue(gAm.backedOffReservations.isEmpty());
    Assert.assertTrue(am.reservations.isEmpty());
    UUID id = gAm.reserve(reservation).getReservationId();
    Assert.assertEquals(gang, !gAm.localReservations.isEmpty());
    Assert.assertTrue(gAm.backedOffReservations.isEmpty());
    Assert.assertEquals(reservation.getResources().get(0).getClientResourceId
        (), gAm.getReservation(id).getResources().get(0).getClientResourceId());
    Assert.assertTrue(am.reservations.containsKey(id));
    gAm.releaseReservation(handle, id);
    Assert.assertTrue(gAm.localReservations.isEmpty());
    Assert.assertTrue(gAm.backedOffReservations.isEmpty());
    Assert.assertFalse(am.reservations.containsKey(id));
    gAm.releaseReservationsForHandle(UUID.randomUUID());
    gAm.releaseReservationsForQueue("q");
    gAm.stop();
    Assert.assertFalse(gAm.isRunning());

    Assert.assertEquals(EXPECTED, am.invoked);
  }

  @Test
  public void testDelegateNoGang() throws Exception {
    testDelegate(false);
  }

  @Test
  public void testDelegateGang() throws Exception {
    testDelegate(true);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGangReserveAllocate() throws Exception {
    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new GangAntiDeadlockLlamaAM(conf, am);
    try {
      gAm.start();
      long lastAllocation = gAm.timeOfLastAllocation;

      Reservation<Resource> reservation = createReservation(UUID.randomUUID(),
          1, true);
      UUID id = gAm.reserve(reservation).getReservationId();
      Assert.assertFalse(gAm.localReservations.isEmpty());
      LlamaAMEventImpl event = new LlamaAMEventImpl(reservation.getHandle());
      event.getAllocatedReservationIds().add(id);
      event.getAllocatedGangResources().add(
          am.reservations.get(id).getResources().get(0));
      Assert.assertEquals(lastAllocation, gAm.timeOfLastAllocation);
      am.dispatch(event);
      Assert.assertNotSame(lastAllocation, gAm.timeOfLastAllocation);
      Assert.assertTrue(gAm.localReservations.isEmpty());
    } finally {
      gAm.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGangReserveReject() throws Exception {
    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new GangAntiDeadlockLlamaAM(conf, am);
    try {
      gAm.start();
      long lastAllocation = gAm.timeOfLastAllocation;

      Reservation<Resource> reservation = createReservation(UUID.randomUUID(),
          1, true);
      UUID id = gAm.reserve(reservation).getReservationId();
      Assert.assertFalse(gAm.localReservations.isEmpty());
      LlamaAMEvent event = new LlamaAMEventImpl(reservation.getHandle());
      event.getRejectedReservationIds().add(id);
      Assert.assertEquals(lastAllocation, gAm.timeOfLastAllocation);
      am.dispatch(event);
      Assert.assertEquals(lastAllocation, gAm.timeOfLastAllocation);
      Assert.assertTrue(gAm.localReservations.isEmpty());
    } finally {
      gAm.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGangReserveBackoffReReserve()
      throws Exception {
    Map<UUID, LlamaAMEventImpl> eventsMap =
        new HashMap<UUID, LlamaAMEventImpl>();

    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new MyGangAntiDeadlockLlamaAM(conf, am);
    try {
      gAm.start();

      //reserve
      UUID handle = UUID.randomUUID();
      Reservation<Resource> reservation1 = createReservation(handle, 1, true);
      UUID id1 = gAm.reserve(reservation1).getReservationId();
      long placedOn1 = gAm.getReservation(id1).getPlacedOn();
      Thread.sleep(1);
      Reservation<Resource> reservation2 = createReservation(handle, 1, true);
      UUID id2 = gAm.reserve(reservation2).getReservationId();
      long placedOn2 = gAm.getReservation(id2).getPlacedOn();
      Thread.sleep(1);
      Reservation<Resource> reservation3 = createReservation(handle, 1, true);
      UUID id3 = gAm.reserve(reservation3).getReservationId();
      long placedOn3 = gAm.getReservation(id3).getPlacedOn();
      Thread.sleep(1);
      Reservation<Resource> reservation4 = createReservation(handle, 1, true);
      UUID id4 = gAm.reserve(reservation4).getReservationId();
      long placedOn4 = gAm.getReservation(id4).getPlacedOn();

      Assert.assertNotNull(gAm.getReservation(id1));
      Assert.assertNotNull(gAm.getReservation(id2));
      Assert.assertNotNull(gAm.getReservation(id3));
      Assert.assertNotNull(gAm.getReservation(id4));

      Assert.assertEquals(4, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(0, gAm.backedOffReservations.size());
      Assert.assertEquals(4, gAm.submittedReservations.size());

      //deadlock avoidance without victims
      gAm.timeOfLastAllocation = System.currentTimeMillis();
      long sleep = gAm.deadlockAvoidance(eventsMap);
      Assert.assertTrue(NO_ALLOCATION_LIMIT >= sleep);
      Assert.assertEquals(4, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(0, gAm.backedOffReservations.size());
      Assert.assertEquals(4, gAm.submittedReservations.size());

      //deadlock avoidance with victims
      gAm.timeOfLastAllocation = System.currentTimeMillis() -
          NO_ALLOCATION_LIMIT - 1;
      sleep = gAm.deadlockAvoidance(eventsMap);
      Assert.assertEquals(NO_ALLOCATION_LIMIT, sleep);
      Assert.assertEquals(2, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(2, gAm.backedOffReservations.size());
      Assert.assertEquals(2, gAm.submittedReservations.size());

      //2nd deadlock avoidance with victims
      gAm.timeOfLastAllocation = System.currentTimeMillis() -
          NO_ALLOCATION_LIMIT;
      sleep = gAm.deadlockAvoidance(eventsMap);
      Assert.assertEquals(NO_ALLOCATION_LIMIT, sleep);
      Assert.assertEquals(1, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(3, gAm.backedOffReservations.size());
      Assert.assertEquals(1, gAm.submittedReservations.size());

      //allocation of survivors
      Set<UUID> stillPlaced = new HashSet<UUID>(am.reservations.keySet());
      LlamaAMEvent event = new LlamaAMEventImpl(UUID.randomUUID());
      event.getAllocatedReservationIds().addAll(stillPlaced);
      am.dispatch(event);
      Assert.assertEquals(1, am.reservations.size());
      Assert.assertEquals(3, gAm.localReservations.size());
      Assert.assertEquals(3, gAm.backedOffReservations.size());
      Assert.assertEquals(0, gAm.submittedReservations.size());

      sleep = gAm.reReserveBackOffs(eventsMap);
      Assert.assertTrue(BACKOFF_MAX_DELAY > sleep);

      Thread.sleep(BACKOFF_MAX_DELAY + 10);
      sleep = gAm.reReserveBackOffs(eventsMap);
      Assert.assertEquals(Long.MAX_VALUE, sleep);
      Assert.assertEquals(4, am.reservations.size());
      Assert.assertEquals(3, gAm.localReservations.size());
      Assert.assertEquals(0, gAm.backedOffReservations.size());
      Assert.assertEquals(3, gAm.submittedReservations.size());

      //verify placedOn value is same as original
      Assert.assertEquals(placedOn1, gAm.getReservation(id1).getPlacedOn());
      Assert.assertEquals(placedOn2, gAm.getReservation(id2).getPlacedOn());
      Assert.assertEquals(placedOn3, gAm.getReservation(id3).getPlacedOn());
      Assert.assertEquals(placedOn4, gAm.getReservation(id4).getPlacedOn());
    } finally {
      gAm.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReleaseReservation() throws Exception {
    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new MyGangAntiDeadlockLlamaAM(conf, am);
    try {
      gAm.start();

      //reserve
      UUID handle = UUID.randomUUID();
      Reservation<Resource> reservation1 = createReservation(handle, 1, true);
      UUID id1 = gAm.reserve(reservation1).getReservationId();
      Reservation<Resource> reservation2 = createReservation(handle, 1, true);
      UUID id2 = gAm.reserve(reservation2).getReservationId();
      Reservation<Resource> reservation3 = createReservation(handle, 1, true);
      UUID id3 = gAm.reserve(reservation3).getReservationId();
      Reservation<Resource> reservation4 = createReservation(handle, 1, true);
      UUID id4 = gAm.reserve(reservation4).getReservationId();

      Map<UUID, LlamaAMEventImpl> eventsMap =
          new HashMap<UUID, LlamaAMEventImpl>();

      //deadlock avoidance with victims
      gAm.timeOfLastAllocation = System.currentTimeMillis() -
          NO_ALLOCATION_LIMIT - 1;
      long sleep = gAm.deadlockAvoidance(eventsMap);
      Assert.assertEquals(NO_ALLOCATION_LIMIT, sleep);
      Assert.assertEquals(2, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(2, gAm.backedOffReservations.size());
      Assert.assertEquals(2, gAm.submittedReservations.size());

      List<UUID> ids = Arrays.asList(id1, id2, id3, id4);
      for (UUID id : ids) {
        PlacedReservation pr = gAm.releaseReservation(handle, id);
        Assert.assertNotNull(pr);
        Assert.assertEquals(PlacedReservation.Status.ENDED, pr.getStatus());
      }
    } finally {
      gAm.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReleaseReservationsForHandle() throws Exception {
    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new MyGangAntiDeadlockLlamaAM(conf, am);
    try {
      gAm.start();

      //reserve
      UUID handle = UUID.randomUUID();
      Reservation<Resource> reservation1 = createReservation(handle, 1, true);
      UUID id1 = gAm.reserve(reservation1).getReservationId();
      Reservation<Resource> reservation2 = createReservation(handle, 1, true);
      UUID id2 = gAm.reserve(reservation2).getReservationId();
      Reservation<Resource> reservation3 = createReservation(handle, 1, true);
      UUID id3 = gAm.reserve(reservation3).getReservationId();
      Reservation<Resource> reservation4 = createReservation(handle, 1, true);
      UUID id4 = gAm.reserve(reservation4).getReservationId();

      Map<UUID, LlamaAMEventImpl> eventsMap =
          new HashMap<UUID, LlamaAMEventImpl>();

      //deadlock avoidance with victims
      gAm.timeOfLastAllocation = System.currentTimeMillis() -
          NO_ALLOCATION_LIMIT - 1;
      long sleep = gAm.deadlockAvoidance(eventsMap);
      Assert.assertEquals(NO_ALLOCATION_LIMIT, sleep);
      Assert.assertEquals(2, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(2, gAm.backedOffReservations.size());
      Assert.assertEquals(2, gAm.submittedReservations.size());

      List<PlacedReservation> prs = gAm.releaseReservationsForHandle(handle);
      Assert.assertEquals(4, prs.size());
      for (PlacedReservation pr : prs) {
        Assert.assertEquals(PlacedReservation.Status.ENDED, pr.getStatus());
      }
    } finally {
      gAm.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReleaseReservationsForQueue() throws Exception {
    Configuration conf = createGangConfig();
    MyLlamaAM am = new MyLlamaAM(conf);
    GangAntiDeadlockLlamaAM gAm = new MyGangAntiDeadlockLlamaAM(conf, am);
    try {
      gAm.start();

      //reserve
      UUID handle = UUID.randomUUID();
      Reservation<Resource> reservation1 = createReservation(handle, 1, true);
      UUID id1 = gAm.reserve(reservation1).getReservationId();
      Reservation<Resource> reservation2 = createReservation(handle, 1, true);
      UUID id2 = gAm.reserve(reservation2).getReservationId();
      Reservation<Resource> reservation3 = createReservation(handle, 1, true);
      UUID id3 = gAm.reserve(reservation3).getReservationId();
      Reservation<Resource> reservation4 = createReservation(handle, 1, true);
      UUID id4 = gAm.reserve(reservation4).getReservationId();

      Map<UUID, LlamaAMEventImpl> eventsMap =
          new HashMap<UUID, LlamaAMEventImpl>();

      //deadlock avoidance with victims
      gAm.timeOfLastAllocation = System.currentTimeMillis() -
          NO_ALLOCATION_LIMIT - 1;
      long sleep = gAm.deadlockAvoidance(eventsMap);
      Assert.assertEquals(NO_ALLOCATION_LIMIT, sleep);
      Assert.assertEquals(2, am.reservations.size());
      Assert.assertEquals(4, gAm.localReservations.size());
      Assert.assertEquals(2, gAm.backedOffReservations.size());
      Assert.assertEquals(2, gAm.submittedReservations.size());

      List<PlacedReservation> prs = gAm.releaseReservationsForQueue("q");
      Assert.assertEquals(4, prs.size());
      for (PlacedReservation pr : prs) {
        Assert.assertEquals(PlacedReservation.Status.ENDED, pr.getStatus());
      }
    } finally {
      gAm.stop();
    }
  }

}