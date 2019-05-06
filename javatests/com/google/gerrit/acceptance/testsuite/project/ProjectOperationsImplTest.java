// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.testsuite.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.truth.ConfigSubject.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestPermission;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Test;

public class ProjectOperationsImplTest extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;

  @Test
  public void defaultName() throws Exception {
    Project.NameKey name = projectOperations.newProject().create();
    // check that the project was created (throws exception if not found.)
    gApi.projects().name(name.get());
    Project.NameKey name2 = projectOperations.newProject().create();
    assertThat(name2).isNotEqualTo(name);
  }

  @Test
  public void specifiedName() throws Exception {
    String name = "somename";
    Project.NameKey key = projectOperations.newProject().name(name).create();
    assertThat(key.get()).isEqualTo(name);
  }

  @Test
  public void emptyCommit() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();

    List<BranchInfo> branches = gApi.projects().name(key.get()).branches().get();
    assertThat(branches).isNotEmpty();
    assertThat(branches.stream().map(x -> x.ref).collect(toList()))
        .isEqualTo(ImmutableList.of("HEAD", "refs/meta/config", "refs/heads/master"));
  }

  @Test
  public void getProjectConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    assertThat(projectOperations.project(key).getProjectConfig().getProject().getDescription())
        .isEmpty();

    ConfigInput input = new ConfigInput();
    input.description = "my fancy project";
    gApi.projects().name(key.get()).config(input);

    assertThat(projectOperations.project(key).getProjectConfig().getProject().getDescription())
        .isEqualTo("my fancy project");
  }

  @Test
  public void mutatingResultOfGetProjectConfigDoesNotMutateGlobalCachedValue() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    ProjectConfig projectConfig = projectOperations.project(key).getProjectConfig();
    ProjectState cachedProjectState1 = projectCache.checkedGet(key);
    ProjectConfig cachedProjectConfig1 = cachedProjectState1.getConfig();
    assertThat(cachedProjectConfig1).isNotSameInstanceAs(projectConfig);
    assertThat(cachedProjectConfig1.getProject().getDescription()).isEmpty();
    assertThat(projectConfig.getProject().getDescription()).isEmpty();
    projectConfig.getProject().setDescription("my fancy project");

    ProjectConfig cachedProjectConfig2 = projectCache.checkedGet(key).getConfig();
    assertThat(cachedProjectConfig2).isNotSameInstanceAs(projectConfig);
    assertThat(cachedProjectConfig2.getProject().getDescription()).isEmpty();
  }

  @Test
  public void getProjectConfigNoRefsMetaConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    deleteRefsMetaConfig(key);

    ProjectConfig projectConfig = projectOperations.project(key).getProjectConfig();
    assertThat(projectConfig.getName()).isEqualTo(key);
    assertThat(projectConfig.getRevision()).isNull();
  }

  @Test
  public void getConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    Config config = projectOperations.project(key).getConfig();
    assertThat(config).isNotInstanceOf(StoredConfig.class);
    assertThat(config).text().isEmpty();

    ConfigInput input = new ConfigInput();
    input.description = "my fancy project";
    gApi.projects().name(key.get()).config(input);

    config = projectOperations.project(key).getConfig();
    assertThat(config).isNotInstanceOf(StoredConfig.class);
    assertThat(config).sections().containsExactly("project");
    assertThat(config).subsections("project").isEmpty();
    assertThat(config).sectionValues("project").containsExactly("description", "my fancy project");
  }

  @Test
  public void getConfigNoRefsMetaConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    deleteRefsMetaConfig(key);

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).isNotInstanceOf(StoredConfig.class);
    assertThat(config).isEmpty();
  }

  @Test
  public void addAllowPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(TestProjectUpdate.allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "group global:Registered-Users");
  }

  @Test
  public void addDenyPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(TestProjectUpdate.deny(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "deny group global:Registered-Users");
  }

  @Test
  public void addBlockPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(TestProjectUpdate.block(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "block group global:Registered-Users");
  }

  @Test
  public void addAllowForcePermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(
            TestProjectUpdate.allow(Permission.ABANDON)
                .ref("refs/foo")
                .group(REGISTERED_USERS)
                .force(true))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "+force group global:Registered-Users");
  }

  @Test
  public void addMultiplePermissions() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(TestProjectUpdate.allow(Permission.ABANDON).ref("refs/foo").group(PROJECT_OWNERS))
        .add(TestProjectUpdate.allow(Permission.CREATE).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Project-Owners",
            "create", "group global:Registered-Users");
  }

  @Test
  public void addDuplicatePermissions() throws Exception {
    TestPermission permission =
        TestProjectUpdate.allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS).build();
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations.project(key).forUpdate().add(permission).add(permission).update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Registered-Users");

    projectOperations.project(key).forUpdate().add(permission).update();
    config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Registered-Users");
  }

  private void deleteRefsMetaConfig(Project.NameKey key) throws Exception {
    try (Repository repo = repoManager.openRepository(key)) {
      new TestRepository<>(repo).delete(REFS_CONFIG);
    }
  }
}
