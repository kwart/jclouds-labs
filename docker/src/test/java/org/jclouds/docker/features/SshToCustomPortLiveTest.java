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
package org.jclouds.docker.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jclouds.compute.options.TemplateOptions.Builder.runAsRoot;
import static org.jclouds.docker.compute.config.LoginPortLookupModule.loginPortLookupBinder;
import static org.jclouds.scriptbuilder.domain.Statements.exec;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.internal.BaseComputeServiceContextLiveTest;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.compute.BaseDockerApiLiveTest;
import org.jclouds.docker.compute.functions.LoginPortForContainer;
import org.jclouds.docker.compute.options.DockerTemplateOptions;
import org.jclouds.docker.domain.Container;
import org.jclouds.docker.domain.Image;
import org.jclouds.docker.options.BuildOptions;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.MapBinder;

@Test(groups = "live", testName = "SshToImageLiveTest", singleThreaded = true)
public class SshToCustomPortLiveTest extends BaseComputeServiceContextLiveTest {

   private static final int SSH_PORT = 8822;
   private static final String IMAGE_REPOSITORY = "jclouds/testrepo";
   private static final String IMAGE_TAG_1 = "testtag";
   private static final String IMAGE_TAG_2 = "second";

   private Image image;

   public SshToCustomPortLiveTest() {
      provider = "docker";
   }

   @Override
   @BeforeClass(groups = { "integration", "live" })
   public void setupContext() {
      super.setupContext();

      final String tag = toTag(IMAGE_REPOSITORY, IMAGE_TAG_1);
      BuildOptions options = BuildOptions.Builder.tag(tag).verbose(false).nocache(false);
      InputStream buildImageStream;
      try {
         buildImageStream = api().getMiscApi().build(BaseDockerApiLiveTest.tarredDockerfile(), options);
         consumeStreamSilently(buildImageStream);
      } catch (IOException e) {
         e.printStackTrace();
      }
      image = api().getImageApi().inspectImage(tag);
      api().getImageApi().tagImage(image.id(), IMAGE_REPOSITORY, IMAGE_TAG_2, true);
   }

   @AfterClass
   protected void tearDown() {
      consumeStreamSilently(api().getImageApi().deleteImage(toTag(IMAGE_REPOSITORY, IMAGE_TAG_1)));
      consumeStreamSilently(api().getImageApi().deleteImage(toTag(IMAGE_REPOSITORY, IMAGE_TAG_2)));
   }

   private DockerApi api() {
      return view.unwrapApi(DockerApi.class);
   }

   @Override
   protected Iterable<Module> setupModules() {
      return ImmutableSet.<Module> of(getLoggingModule(), new SshjSshClientModule(), new AbstractModule() {
         @Override
         protected void configure() {
            bind(LoginPortForContainer.class).toInstance(new LoginPortForContainer() {
               @Override
               public Optional<Integer> apply(Container input) {
                  return Optional.of(SSH_PORT);
               }
            });
         }
      });
   }

   private static void consumeStreamSilently(InputStream is) {
      char[] tmpBuff = new char[8 * 1024];
      // throw everything away
      InputStreamReader isr = new InputStreamReader(is);

      try {
         try {
            while (isr.read(tmpBuff) > -1)
               ;
         } finally {
            isr.close();
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   private static String toTag(String repo, String tag) {
      return repo + (tag != null ? ":" + tag : "");
   }

   @Test
   public void testSsh() throws RunNodesException {

      final DockerTemplateOptions options = DockerTemplateOptions.Builder
            .commands("/usr/sbin/dropbear", "-E", "-F", "-p", String.valueOf(SSH_PORT)).overrideLoginUser("root")
            .overrideLoginPassword("screencast").blockOnPort(SSH_PORT, 30).networkMode("host");

      final Template template = view.getComputeService().templateBuilder().imageId(image.id()).options(options).build();

      String nodeId = null;
      try {

         NodeMetadata node = Iterables
               .getOnlyElement(view.getComputeService().createNodesInGroup("ssh-test", 1, template));

         nodeId = node.getId();
         ExecResponse response = view.getComputeService().runScriptOnNode(nodeId, "echo hello",
               runAsRoot(false).wrapInInitScript(false));
         assertThat(response.getOutput().trim()).endsWith("hello");
      } finally {
         if (nodeId != null)
            view.getComputeService().destroyNode(nodeId);
      }
   }

   /**
    * JSON mapping object for progress messages returned in build() method
    * InputStream.
    */
   public static class StreamMessage {

      private String stream;

      @Override
      public String toString() {
         return stream;
      }
   }
}
