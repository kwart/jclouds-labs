/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.azurecompute.features;

import static com.google.common.collect.Iterables.transform;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.jclouds.azurecompute.domain.AffinityGroup;
import org.jclouds.azurecompute.domain.Disk;
import org.jclouds.azurecompute.domain.Location;
import org.jclouds.azurecompute.domain.OSImage;
import org.jclouds.azurecompute.internal.AbstractAzureComputeApiLiveTest;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

@Test(groups = "live", testName = "DiskApiLiveTest")
public class DiskApiLiveTest extends AbstractAzureComputeApiLiveTest {

   private ImmutableSet<String> locations;

   private ImmutableSet<String> images;

   private ImmutableSet<String> groups;

   @BeforeClass(groups = {"integration", "live"})
   @Override
   public void setup() {
      super.setup();

      locations = ImmutableSet.copyOf(transform(api.getLocationApi().list(),
              new Function<Location, String>() {

                 @Override
                 public String apply(final Location location) {
                    return location.name();
                 }
              }));
      images = ImmutableSet.copyOf(transform(api.getOSImageApi().list(), new Function<OSImage, String>() {

         @Override
         public String apply(final OSImage image) {
            return image.name();
         }
      }));
      groups = ImmutableSet.copyOf(transform(api.getAffinityGroupApi().list(), new Function<AffinityGroup, String>() {

         @Override
         public String apply(final AffinityGroup group) {
            return group.name();
         }
      }));
   }

   public void testList() {
      for (Disk disk : api().list()) {
         checkDisk(disk);
      }
   }

   // TODO testDeleteDisk, if we will need testCreateDisk
   private void checkDisk(Disk disk) {
      assertNotNull(disk.name(), "Name cannot be null for: " + disk);

      if (disk.attachedTo() != null) {
         // TODO: verify you can lookup the role
      }

      if (disk.logicalSizeInGB() != null) {
         assertTrue(disk.logicalSizeInGB() > 0, "LogicalSizeInGB should be positive, if set" + disk);
      }

      if (disk.mediaLink() != null) {
         assertTrue(ImmutableSet.of("http", "https").contains(disk.mediaLink().getScheme()),
                 "MediaLink should be an http(s) url" + disk);
      }

      if (disk.location() != null) {
         assertTrue(locations.contains(disk.location()), "Location not in " + locations + " :" + disk);
      }

      if (disk.sourceImage() != null) {
      //TODO disk can be generated from a `VM Image` that listDisk doesn't consider
      //   assertTrue(images.contains(disk.sourceImage()), "SourceImage not in " + images + " :" + disk);
      }

      if (disk.affinityGroup() != null) {
         assertTrue(groups.contains(disk.affinityGroup()), "AffinityGroup not in " + groups + " :" + disk);
      }
   }

   private DiskApi api() {
      return api.getDiskApi();
   }
}
