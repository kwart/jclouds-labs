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

import static org.jclouds.docker.compute.config.LoginPortLookupModule.loginPortLookupBinder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.internal.BaseComputeServiceContextLiveTest;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.compute.BaseDockerApiLiveTest;
import org.jclouds.docker.compute.functions.LoginPortForContainer;
import org.jclouds.docker.compute.options.DockerTemplateOptions;
import org.jclouds.docker.domain.Container;
import org.jclouds.docker.domain.Exec;
import org.jclouds.docker.domain.Image;
import org.jclouds.docker.options.BuildOptions;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.sshj.config.SshjSshClientModule;
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

   private Container container = null;
   private Image image = null;
   private Exec exec = null;

   public SshToCustomPortLiveTest() {
      provider = "docker";
   }

   @Override
   @BeforeClass(groups = { "integration", "live" })
   public void setupContext() {
      super.setupContext();
   }

   private DockerApi api() {
      return view.unwrapApi(DockerApi.class);
   }

   @Override
   protected Iterable<Module> setupModules() {
      return ImmutableSet.<Module> of(getLoggingModule(), new SshjSshClientModule(), new AbstractModule() {
         @Override
         protected void configure() {
            MapBinder<String, LoginPortForContainer> imageToFunction = loginPortLookupBinder(binder());
            imageToFunction.addBinding("testBuildImage").toInstance(new LoginPortForContainer() {
               @Override
               public Optional<Integer> apply(Container input) {
                  return Optional.of(8822);
               }
            });
         }
      });
   }

   @Test
   public void testBuildAndTagImage() throws IOException, InterruptedException, URISyntaxException {
      BuildOptions options = BuildOptions.Builder.tag("testBuildTag").verbose(false).nocache(false);
      InputStream buildImageStream = api().getMiscApi().build(BaseDockerApiLiveTest.tarredDockerfile(), options);
      BaseDockerApiLiveTest.consumeStream(buildImageStream);
      image = api().getImageApi().inspectImage("testBuildImage");
      assertTrue(api().getImageApi().tagImage(image.id(), null, "secondTag", true));
   }

   @Test(dependsOnMethods = "testBuildAndTagImage")
   public void testSsh() {
      DockerTemplateOptions options = DockerTemplateOptions.Builder.commands("/usr/sbin/dropbear", "-E", "-F", "-p",
            "8822").overrideLoginUser("root").overrideLoginPassword("screencast").blockOnPort(8822, 30);
      
      ComputeServiceContext sshContext = null;
      try {

         NodeMetadata node = Iterables.getOnlyElement(view.getComputeService().createNodesInGroup("ssh-test", 1, options));

         NodeMetadata first = get(nodes, 0);
         assert first.getCredentials() != null : first;
         assert first.getCredentials().identity != null : first;
         // credentials should not be present as the import public key call doesn't have access to
         // the related private key
         assert first.getCredentials().credential == null : first;

         AWSRunningInstance instance = getInstance(instanceApi, first.getProviderId());

         assertEquals(instance.getKeyName(), "jclouds#" + group);

         Map<? extends NodeMetadata, ExecResponse> responses = view.getComputeService()
               .runScriptOnNodesMatching(
                     runningInGroup(group),
                     exec("echo hello"),
                     overrideLoginCredentials(
                           LoginCredentials.builder().user(first.getCredentials().identity)
                                 .privateKey(keyPair.get("private")).build()).wrapInInitScript(false).runAsRoot(false));

         ExecResponse hello = getOnlyElement(responses.values());
         assertEquals(hello.getOutput().trim(), "hello");

      } finally {
         sshContext.close();
         view.getComputeService().destroyNodesMatching(inGroup(group));
      }
   }

}
