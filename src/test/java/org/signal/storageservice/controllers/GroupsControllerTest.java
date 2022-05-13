/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signal.libsignal.zkgroup.NotarySignature;
import org.signal.libsignal.zkgroup.groups.GroupPublicParams;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.PniCredentialPresentation;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.storageservice.providers.ProtocolBufferMediaType;
import org.signal.storageservice.storage.protos.groups.AccessControl;
import org.signal.storageservice.storage.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.storage.protos.groups.ExternalGroupCredential;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions;
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions.ModifyAvatarAction;
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions.ModifyTitleAction;
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions.PromoteMemberPendingPniAciProfileKeyAction;
import org.signal.storageservice.storage.protos.groups.GroupChanges;
import org.signal.storageservice.storage.protos.groups.GroupChanges.GroupChangeState;
import org.signal.storageservice.storage.protos.groups.GroupJoinInfo;
import org.signal.storageservice.storage.protos.groups.Member;
import org.signal.storageservice.storage.protos.groups.Member.Role;
import org.signal.storageservice.storage.protos.groups.MemberPendingAdminApproval;
import org.signal.storageservice.storage.protos.groups.MemberPendingProfileKey;
import org.signal.storageservice.util.AuthHelper;

class GroupsControllerTest extends BaseGroupsControllerTest {

  @Test
  void testCreateGroup() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                          eq(0),
                                          any(GroupChange.class),
                                          any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testCreateGroupBadAvatar() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar("groups/" + Base64.getUrlEncoder().withoutPadding().encodeToString(groupPublicParams.getGroupIdentifier().serialize()) + "/foo")
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }


  @Test
  void testCreateGroupConflict() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation presentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(false));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(presentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @Test
  void testCreateGroupLogConflict() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation presentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));
    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                          eq(0),
                                          any(GroupChange.class),
                                          any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(false));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(presentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }


  @Test
  void testCreateGroupNotAdmin() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testCreateGroupNoMembers() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testCreateGroupNoKey() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testCreateGroupBadVersion() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(1)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testCreateGroupUnknownField() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .mergeUnknownFields(UnknownFieldSet.newBuilder().addField(4095, UnknownFieldSet.Field.newBuilder().addVarint(42).build()).build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(422);
    verify(groupsManager, never()).createGroup(any(), any());
  }

  @Test
  void testCreateGroupNoAccessControl() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testCreateGroupBadMember() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);

    when(groupsManager.createGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())),
                                   any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(validUserPresentation.serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setPresentation(ByteString.copyFrom(new byte[validUserPresentation.serialize().length]))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .put(Entity.entity(group.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testGetGroup() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    byte[] entity = response.readEntity(InputStream.class).readAllBytes();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(Group.parseFrom(entity)).isEqualTo(group);
  }

  @Test
  void testGetGroupJoinInfo() throws Exception {
    final byte[] inviteLinkPassword = new byte[16];
    new SecureRandom().nextBytes(inviteLinkPassword);
    final String inviteLinkPasswordString = Base64.getUrlEncoder().withoutPadding().encodeToString(inviteLinkPassword);

    final Group.Builder groupBuilder = Group.newBuilder();
    groupBuilder.setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()));
    groupBuilder.getAccessControlBuilder().setMembers(AccessControl.AccessRequired.MEMBER);
    groupBuilder.getAccessControlBuilder().setAttributes(AccessControl.AccessRequired.MEMBER);
    groupBuilder.setTitle(ByteString.copyFromUtf8("Some title"));
    groupBuilder.setDescription(ByteString.copyFromUtf8("Some description"));
    final String avatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());
    groupBuilder.setAvatar(avatar);
    groupBuilder.setVersion(0);
    groupBuilder.addMembersBuilder()
                .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                .setRole(Member.Role.ADMINISTRATOR)
                .setJoinedAtVersion(0);
    groupBuilder.addMembersBuilder()
                .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                .setRole(Member.Role.DEFAULT)
                .setJoinedAtVersion(0);

    setMockGroupState(groupBuilder);

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/join/" + inviteLinkPasswordString)
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();

    groupBuilder.setInviteLinkPassword(ByteString.copyFrom(inviteLinkPassword));

    setMockGroupState(groupBuilder);

    response = resources.getJerseyTest()
                                 .target("/v1/groups/join/" + inviteLinkPasswordString)
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();

    groupBuilder.getAccessControlBuilder().setAddFromInviteLink(AccessControl.AccessRequired.ANY);
    groupBuilder.setVersion(42);

    setMockGroupState(groupBuilder);

    response = resources.getJerseyTest()
                        .target("/v1/groups/join/" + inviteLinkPasswordString)
                        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                        .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.parseFrom(response.readEntity(InputStream.class).readAllBytes());
    assertThat(groupJoinInfo.getPublicKey().toByteArray()).isEqualTo(groupPublicParams.serialize());
    assertThat(groupJoinInfo.getTitle().toByteArray()).isEqualTo("Some title".getBytes(StandardCharsets.UTF_8));
    assertThat(groupJoinInfo.getDescription().toByteArray()).isEqualTo("Some description".getBytes(StandardCharsets.UTF_8));
    assertThat(groupJoinInfo.getAvatar()).isEqualTo(avatar);
    assertThat(groupJoinInfo.getMemberCount()).isEqualTo(2);
    assertThat(groupJoinInfo.getAddFromInviteLink()).isEqualTo(AccessControl.AccessRequired.ANY);
    assertThat(groupJoinInfo.getVersion()).isEqualTo(42);
    assertThat(groupJoinInfo.getPendingAdminApproval()).isFalse();
    assertThat(groupJoinInfo.getPendingAdminApprovalFull()).isFalse();

    groupBuilder.setVersion(0);

    setMockGroupState(groupBuilder);

    response = resources.getJerseyTest()
                        .target("/v1/groups/join/foo" + inviteLinkPasswordString)
                        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                        .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();

    groupBuilder.getAccessControlBuilder().setAddFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE);

    setMockGroupState(groupBuilder);

    response = resources.getJerseyTest()
                        .target("/v1/groups/join/" + inviteLinkPasswordString)
                        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                        .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();

    groupBuilder.addMembersPendingAdminApprovalBuilder()
                .setUserId(ByteString.copyFrom(validUserThreePresentation.getUuidCiphertext().serialize()))
                .setProfileKey(ByteString.copyFrom(validUserThreePresentation.getProfileKeyCiphertext().serialize()))
                .setTimestamp(System.currentTimeMillis());

    setMockGroupState(groupBuilder);

    response = resources.getJerseyTest()
                        .target("/v1/groups/join/" + inviteLinkPasswordString)
                        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                        .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");
    groupJoinInfo = GroupJoinInfo.parseFrom(response.readEntity(InputStream.class).readAllBytes());
    assertThat(groupJoinInfo.getPublicKey().toByteArray()).isEqualTo(groupPublicParams.serialize());
    assertThat(groupJoinInfo.getTitle().toByteArray()).isEqualTo("Some title".getBytes(StandardCharsets.UTF_8));
    assertThat(groupJoinInfo.getDescription().toByteArray()).isEqualTo("Some description".getBytes(StandardCharsets.UTF_8));
    assertThat(groupJoinInfo.getAvatar()).isEqualTo(avatar);
    assertThat(groupJoinInfo.getMemberCount()).isEqualTo(2);
    assertThat(groupJoinInfo.getAddFromInviteLink()).isEqualTo(AccessControl.AccessRequired.UNSATISFIABLE);
    assertThat(groupJoinInfo.getVersion()).isEqualTo(0);
    assertThat(groupJoinInfo.getPendingAdminApproval()).isTrue();
    assertThat(groupJoinInfo.getPendingAdminApprovalFull()).isFalse();

    setMockGroupState(groupBuilder);

    response = resources.getJerseyTest()
                        .target("/v1/groups/join/")
                        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                        .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");
    groupJoinInfo = GroupJoinInfo.parseFrom(response.readEntity(InputStream.class).readAllBytes());
    assertThat(groupJoinInfo.getPublicKey().toByteArray()).isEqualTo(groupPublicParams.serialize());
    assertThat(groupJoinInfo.getTitle().toByteArray()).isEqualTo("Some title".getBytes(StandardCharsets.UTF_8));
    assertThat(groupJoinInfo.getDescription().toByteArray()).isEqualTo("Some description".getBytes(StandardCharsets.UTF_8));
    assertThat(groupJoinInfo.getAvatar()).isEqualTo(avatar);
    assertThat(groupJoinInfo.getMemberCount()).isEqualTo(2);
    assertThat(groupJoinInfo.getAddFromInviteLink()).isEqualTo(AccessControl.AccessRequired.UNSATISFIABLE);
    assertThat(groupJoinInfo.getVersion()).isEqualTo(0);
    assertThat(groupJoinInfo.getPendingAdminApproval()).isTrue();
    assertThat(groupJoinInfo.getPendingAdminApprovalFull()).isFalse();
  }

  @Test
  void testGetGroupUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();
  }

  @Test
  void testGetGroupNotFound() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.hasEntity()).isFalse();
  }

  @Test
  void testModifyBadAvatar() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyAvatar(ModifyAvatarAction.newBuilder().setAvatar("groups/somethingelse/bar").build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testModifyGroupTitle() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                          .setTitle(ByteString.copyFromUtf8("Another title")))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getTitle().toStringUtf8()).isEqualTo("Another title");
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setTitle(ByteString.copyFromUtf8("Some title"))
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testModifyGroupTitleUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                          .setTitle(ByteString.copyFromUtf8("Another title")))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testModifyGroupTitleAndUnknownField() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                          .setTitle(ByteString.copyFromUtf8("Another title")))
                                                         .mergeUnknownFields(UnknownFieldSet.newBuilder().addField(4095, UnknownFieldSet.Field.newBuilder().addVarint(42).build()).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(422);

    verify(groupsManager, never()).updateGroup(any(), any());
    verify(groupsManager, never()).appendChangeRecord(any(), anyInt(), any(), any());
  }

  @Test
  void testModifyGroupTitleWhenTooLong() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                              .setTitle(ByteString.copyFromUtf8(
                                                                                  "A".repeat(2047))))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);

    verify(groupsManager, never()).updateGroup(any(), any());
    verify(groupsManager, never()).appendChangeRecord(any(), anyInt(), any(), any());
  }

  @Test
  void testModifyGroupDescription() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setDescription(ByteString.copyFromUtf8("Some description"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .setModifyDescription(Actions.ModifyDescriptionAction.newBuilder()
                                                                                                  .setDescription(ByteString.copyFromUtf8("Another description")))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getDescription().toStringUtf8()).isEqualTo("Another description");
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setDescription(ByteString.copyFromUtf8("Some description"))
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(signedChange.getChangeEpoch()).isEqualTo(2);
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testModifyGroupAnnouncementsOnly() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
        .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
        .setAccessControl(AccessControl.newBuilder()
            .setMembers(AccessControl.AccessRequired.MEMBER)
            .setAttributes(AccessControl.AccessRequired.MEMBER))
        .setTitle(ByteString.copyFromUtf8("Some title"))
        .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
        .setVersion(0)
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.ADMINISTRATOR)
            .build())
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.DEFAULT)
            .build())
        .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
        .setVersion(1)
        .setModifyAnnouncementsOnly(Actions.ModifyAnnouncementsOnlyAction.newBuilder().setAnnouncementsOnly(true))
        .build();

    Response response = resources.getJerseyTest()
        .target("/v1/groups/")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
        .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
    verify(groupsManager, never()).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any());
    verify(groupsManager, never()).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), anyInt(), any(), any());

    response = resources.getJerseyTest()
        .target("/v1/groups/")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
        .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getAnnouncementsOnly()).isTrue();
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
        .setAnnouncementsOnly(false)
        .setVersion(0)
        .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(signedChange.getChangeEpoch()).isEqualTo(3);
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
        new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testModifyGroupAvatarAndTitle() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    String someAvatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(someAvatar)
                       .setVersion(1)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(2), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    String anotherAvatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(2)
                                                         .setModifyAvatar(ModifyAvatarAction.newBuilder()
                                                                                            .setAvatar(anotherAvatar))
                                                         .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                          .setTitle(ByteString.copyFromUtf8("Another title")))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(2), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getTitle().toStringUtf8()).isEqualTo("Another title");
    assertThat(captor.getValue().getAvatar()).isEqualTo(anotherAvatar);
    assertThat(captor.getValue().getVersion()).isEqualTo(2);

    assertThat(captor.getValue().toBuilder()
                     .setTitle(ByteString.copyFromUtf8("Some title"))
                     .setAvatar(someAvatar)
                     .setVersion(1)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(2);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testModifyGroupTimer() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyDisappearingMessageTimer(Actions.ModifyDisappearingMessageTimerAction.newBuilder()
                                                                                                                                        .setTimer(ByteString.copyFromUtf8("Another timer"))
                                                                                                                                        .build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getDisappearingMessagesTimer().toStringUtf8()).isEqualTo("Another timer");
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setDisappearingMessagesTimer(ByteString.EMPTY)
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testModifyGroupTimerUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyDisappearingMessageTimer(Actions.ModifyDisappearingMessageTimerAction.newBuilder().setTimer(ByteString.copyFromUtf8("Another timer")).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testDeleteMember() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addDeleteMembers(Actions.DeleteMemberAction.newBuilder()
                                                                                                     .setDeletedUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                     .build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(1);
    assertThat(captor.getValue().getMembers(0).getUserId()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .addMembers(Member.newBuilder()
                                       .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                       .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                       .setRole(Member.Role.DEFAULT)
                                       .build())
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testDeleteMemberUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addDeleteMembers(Actions.DeleteMemberAction.newBuilder()
                                                                                                     .setDeletedUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize())).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }


  @Test
  void testAddMember() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                                                                                               .setRole(Member.Role.DEFAULT)
                                                                                                               .build()))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(2);
    assertThat(captor.getValue().getMembers(1).getUserId()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getProfileKey()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getPresentation()).isEmpty();
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .removeMembers(1)
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));

    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearVersion().clearSourceUuid().build())
        .isEqualTo(Actions.newBuilder().addAddMembers(Actions.AddMemberAction.newBuilder().setAdded(Member.newBuilder().setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                          .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                                                                          .setRole(Member.Role.DEFAULT)
                                                                                                          .build())
                                                                             .build())
                          .build());

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testAddMemberSetJoinedViaInviteLink() {
    final Group.Builder groupBuilder = Group.newBuilder();
    groupBuilder.setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()));
    groupBuilder.getAccessControlBuilder().setMembers(AccessControl.AccessRequired.MEMBER);
    groupBuilder.getAccessControlBuilder().setAttributes(AccessControl.AccessRequired.MEMBER);
    groupBuilder.setTitle(ByteString.copyFromUtf8("Some title"));
    final String avatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());
    groupBuilder.setAvatar(avatar);
    groupBuilder.setVersion(0);
    groupBuilder.addMembersBuilder()
                .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                .setRole(Member.Role.ADMINISTRATOR)
                .setJoinedAtVersion(0);
    groupBuilder.addMembersBuilder()
                .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                .setRole(Member.Role.DEFAULT)
                .setJoinedAtVersion(0);

    Group group = groupBuilder.build();
    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserThreePresentation.serialize()))
                                                                                                               .setRole(Member.Role.DEFAULT)
                                                                                                               .build())
                                                                                               .setJoinFromInviteLink(true)
                                                                                               .build())
                                                         .build();
    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(400);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testAddMemberWhoIsAlreadyPendingProfileKey() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    final MemberPendingProfileKey memberPendingProfileKey = MemberPendingProfileKey.newBuilder()
                                                                 .setAddedByUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                                                 .setTimestamp(Clock.systemUTC().millis())
                                                                 .setMember(Member.newBuilder()
                                                                                  .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                  .setRole(Member.Role.DEFAULT)
                                                                                  .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                                                  .build())
                                                                 .build();
    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembersPendingProfileKey(memberPendingProfileKey)
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                                                                                               .setRole(Member.Role.DEFAULT)
                                                                                                               .build()))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(2);
    assertThat(captor.getValue().getMembers(1).getUserId()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getProfileKey()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getPresentation()).isEmpty();
    assertThat(captor.getValue().getMembersPendingProfileKeyCount()).isEqualTo(0);
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .removeMembers(1)
                     .setVersion(0)
                     .addMembersPendingProfileKey(memberPendingProfileKey)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));

    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearVersion().clearSourceUuid().build())
            .isEqualTo(Actions.newBuilder().addAddMembers(Actions.AddMemberAction.newBuilder().setAdded(Member.newBuilder().setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                              .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                                                                              .setRole(Member.Role.DEFAULT)
                                                                                                              .build())
                                                                                 .build())
                              .build());

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testAddMemberWhoIsAlreadyPendingAdminApproval() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    final MemberPendingAdminApproval memberPendingAdminApproval = MemberPendingAdminApproval.newBuilder()
                                                                                            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                            .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                                                            .setTimestamp(Clock.systemUTC().millis())
                                                                                            .build();
    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembersPendingAdminApproval(memberPendingAdminApproval)
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
            .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                                                                                               .setRole(Member.Role.DEFAULT)
                                                                                                               .build()))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(2);
    assertThat(captor.getValue().getMembers(1).getUserId()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getProfileKey()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getPresentation()).isEmpty();
    assertThat(captor.getValue().getMembersPendingAdminApprovalCount()).isEqualTo(0);
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .removeMembers(1)
                     .setVersion(0)
                     .addMembersPendingAdminApproval(memberPendingAdminApproval)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));

    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearVersion().clearSourceUuid().build())
            .isEqualTo(Actions.newBuilder().addAddMembers(Actions.AddMemberAction.newBuilder().setAdded(Member.newBuilder().setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                              .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                                                                              .setRole(Member.Role.DEFAULT)
                                                                                                              .build())
                                                                                 .build())
                              .build());

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testAddMemberUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                                                                                               .setRole(Member.Role.DEFAULT).build()).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testJoinNonPublicGroup() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                                                                                               .setRole(Member.Role.DEFAULT).build()).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));


    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void testAddAdminUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addAddMembers(Actions.AddMemberAction.newBuilder()
                                                                                               .setAdded(Member.newBuilder()
                                                                                                               .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize()))
                                                                                                               .setRole(Member.Role.ADMINISTRATOR).build()).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testModifyMemberPresentation() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation          = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation       = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentationUpdate = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addModifyMemberProfileKeys(Actions.ModifyMemberProfileKeyAction.newBuilder()
                                                                                                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentationUpdate.serialize())))
                                                         .build();

    GroupChange.Actions.Builder expectedGroupChangeResponseBuilder = groupChange.toBuilder()
        .setSourceUuid(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    expectedGroupChangeResponseBuilder.getModifyMemberProfileKeysBuilder(0)
        .setUserId(ByteString.copyFrom(validUserTwoPresentationUpdate.getUuidCiphertext().serialize()))
        .setProfileKey(ByteString.copyFrom(validUserTwoPresentationUpdate.getProfileKeyCiphertext().serialize()));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(2);
    assertThat(captor.getValue().getMembers(1).getProfileKey()).isEqualTo(ByteString.copyFrom(validUserTwoPresentationUpdate.getProfileKeyCiphertext().serialize()));
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setMembers(1, captor.getValue().getMembers(1).toBuilder().setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize())))
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions())).isEqualTo(expectedGroupChangeResponseBuilder.build());

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testModifyMemberPresentationUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation          = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation       = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);
    ProfileKeyCredentialPresentation validUserTwoPresentationUpdate = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addModifyMemberProfileKeys(Actions.ModifyMemberProfileKeyAction.newBuilder()
                                                                                                                         .setPresentation(ByteString.copyFrom(validUserTwoPresentationUpdate.serialize())))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void testAddMemberPendingProfileKey() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addAddMembersPendingProfileKey(Actions.AddMemberPendingProfileKeyAction.newBuilder()
                                                                                                                     .setAdded(MemberPendingProfileKey.newBuilder()
                                                                                                                                                      .setMember(Member.newBuilder()
                                                                                                                                                                       .setRole(Member.Role.DEFAULT)
                                                                                                                                                                       .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                                                                                       .build())
                                                                                                                                                      .build())
                                                                                                                     .build())
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor            = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor      = ArgumentCaptor.forClass(GroupChange.class);
    ArgumentCaptor<Group>       changeStateCaptor = ArgumentCaptor.forClass(Group.class      );

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), changeStateCaptor.capture());

    assertThat(captor.getValue().getMembersCount()).isEqualTo(1);
    assertThat(captor.getValue().getMembersPendingProfileKeyCount()).isEqualTo(1);
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getMember().getUserId()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getMember().getRole()).isEqualTo(Member.Role.DEFAULT);
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getMember().getProfileKey()).isEmpty();
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getMember().getPresentation()).isEmpty();
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getMember().getJoinedAtVersion()).isEqualTo(1);
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getAddedByUserId()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getMembersPendingProfileKey(0).getTimestamp()).isLessThanOrEqualTo(System.currentTimeMillis()).isGreaterThan(System.currentTimeMillis() - 5000);

    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .removeMembersPendingProfileKey(0)
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testAddMemberPendingProfileKeyNotMember() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addAddMembersPendingProfileKey(Actions.AddMemberPendingProfileKeyAction
                                                                                     .newBuilder()
                                                                                     .setAdded(MemberPendingProfileKey.newBuilder().setMember(Member.newBuilder()
                                                                                                                                                    .setRole(Member.Role.DEFAULT)
                                                                                                                                                    .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                                                                    .build())
                                                                                                                      .build())
                                                                                     .build())
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void testAddMemberPendingProfileKeyUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addAddMembersPendingProfileKey(Actions.AddMemberPendingProfileKeyAction.newBuilder()
                                                                                                                     .setAdded(MemberPendingProfileKey.newBuilder()
                                                                                                                                                      .setMember(Member.newBuilder()
                                                                                                                                                                       .setRole(Member.Role.DEFAULT)
                                                                                                                                                                       .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize())).build())
                                                                                                                                                      .build())
                                                                                                                     .build())
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void testDeleteMemberPendingProfileKeyAsAdmin() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
                                                                           .setAddedByUserId(ByteString.copyFromUtf8("someone"))
                                                                           .setTimestamp(System.currentTimeMillis())
                                                                           .setMember(Member.newBuilder()
                                                                                            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                            .setRole(Member.Role.DEFAULT)
                                                                                            .build())
                                                                           .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addDeleteMembersPendingProfileKey(Actions.DeleteMemberPendingProfileKeyAction.newBuilder()
                                                                                                                           .setDeletedUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize())))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(1);
    assertThat(captor.getValue().getMembersPendingProfileKeyCount()).isEqualTo(0);

    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setVersion(0)
                     .build()).isEqualTo(group.toBuilder().removeMembersPendingProfileKey(0).build());

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testDeleteMemberPendingProfileKeyAsInvitee() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
                                                                           .setAddedByUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                                                           .setTimestamp(System.currentTimeMillis())
                                                                           .setMember(Member.newBuilder()
                                                                                            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                            .setRole(Member.Role.DEFAULT)
                                                                                            .build())
                                                                           .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addDeleteMembersPendingProfileKey(Actions.DeleteMemberPendingProfileKeyAction.newBuilder()
                                                                                                                           .setDeletedUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize())))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(1);
    assertThat(captor.getValue().getMembersPendingProfileKeyCount()).isEqualTo(0);

    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setVersion(0)
                     .build()).isEqualTo(group.toBuilder().removeMembersPendingProfileKey(0).build());

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testDeleteMemberPendingProfileKeyUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation      = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL      );
    ProfileKeyCredentialPresentation validUserTwoPresentation   = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL  );
    ProfileKeyCredentialPresentation validUserThreePresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_THREE_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
                                                                           .setAddedByUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                           .setTimestamp(System.currentTimeMillis())
                                                                           .setMember(Member.newBuilder()
                                                                                            .setUserId(ByteString.copyFrom(validUserThreePresentation.getUuidCiphertext().serialize()))
                                                                                            .setRole(Member.Role.DEFAULT)
                                                                                            .build())
                                                                           .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addDeleteMembersPendingProfileKey(Actions.DeleteMemberPendingProfileKeyAction.newBuilder()
                                                                                                                           .setDeletedUserId(ByteString.copyFrom(validUserThreePresentation.getUuidCiphertext().serialize())))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void testAcceptMemberPendingProfileKeyInvitation() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
                                                                           .setAddedByUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                                                           .setTimestamp(System.currentTimeMillis())
                                                                           .setMember(Member.newBuilder()
                                                                                            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                            .setRole(Member.Role.DEFAULT)
                                                                                            .build())
                                                                           .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addPromoteMembersPendingProfileKey(Actions.PromoteMemberPendingProfileKeyAction.newBuilder()
                                                                                                                             .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize())))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(2);
    assertThat(captor.getValue().getMembers(1).getJoinedAtVersion()).isEqualTo(1);
    assertThat(captor.getValue().getMembers(1).getPresentation()).isEmpty();
    assertThat(captor.getValue().getMembers(1).getProfileKey()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getRole()).isEqualTo(Member.Role.DEFAULT);
    assertThat(captor.getValue().getMembers(1).getUserId()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));
    assertThat(captor.getValue().getMembersPendingProfileKeyCount()).isEqualTo(0);

    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setVersion(0)
                     .build()).isEqualTo(group.toBuilder()
                                              .removeMembersPendingProfileKey(0)
                                              .addMembers(Member.newBuilder()
                                                                .setRole(Member.Role.DEFAULT)
                                                                .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                                                .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                .setJoinedAtVersion(1)
                                                                .build())
                                              .build());

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()));

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  void testAcceptMemberPendingProfileKeyInvitationUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
                                                                           .setAddedByUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                                                           .setTimestamp(System.currentTimeMillis())
                                                                           .setMember(Member.newBuilder()
                                                                                            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                            .setRole(Member.Role.DEFAULT)
                                                                                            .build())
                                                                           .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
                                             .setVersion(1)
                                             .addPromoteMembersPendingProfileKey(Actions.PromoteMemberPendingProfileKeyAction.newBuilder()
                                                                                                                             .setPresentation(ByteString.copyFrom(validUserTwoPresentation.serialize())))
                                             .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  public void testAcceptMemberPendingPniAciProfileKeyInvitation() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation =
        new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams())
            .createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);

    PniCredentialPresentation validUserTwoPniPresentation =
        new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams())
            .createPniCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PNI_CREDENTIAL);

    Group group = Group.newBuilder()
        .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
        .setAccessControl(AccessControl.newBuilder()
            .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
            .setAttributes(AccessControl.AccessRequired.MEMBER))
        .setTitle(ByteString.copyFromUtf8("Some title"))
        .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
        .setVersion(0)
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.ADMINISTRATOR)
            .build())
        .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
            .setAddedByUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setTimestamp(System.currentTimeMillis())
            .setMember(Member.newBuilder()
                .setUserId(ByteString.copyFrom(validUserTwoPniPresentation.getPniCiphertext().serialize()))
                .setRole(Member.Role.DEFAULT)
                .build())
            .build())
        .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
        .setVersion(1)
        .addPromoteMembersPendingPniAciProfileKey(Actions.PromoteMemberPendingPniAciProfileKeyAction.newBuilder()
            .setPresentation(ByteString.copyFrom(validUserTwoPniPresentation.serialize())))
        .build();

    Response response = resources.getJerseyTest()
        .target("/v1/groups/")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_PNI_AUTH_CREDENTIAL))
        .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembersCount()).isEqualTo(2);
    assertThat(captor.getValue().getMembers(1).getJoinedAtVersion()).isEqualTo(1);
    assertThat(captor.getValue().getMembers(1).getPresentation()).isEmpty();
    assertThat(captor.getValue().getMembers(1).getProfileKey()).isEqualTo(ByteString.copyFrom(validUserTwoPniPresentation.getProfileKeyCiphertext().serialize()));
    assertThat(captor.getValue().getMembers(1).getRole()).isEqualTo(Member.Role.DEFAULT);
    assertThat(captor.getValue().getMembers(1).getUserId()).isEqualTo(ByteString.copyFrom(validUserTwoPniPresentation.getAciCiphertext().serialize()));
    assertThat(captor.getValue().getMembersPendingProfileKeyCount()).isEqualTo(0);

    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
        .setVersion(0)
        .build()).isEqualTo(group.toBuilder()
        .removeMembersPendingProfileKey(0)
        .addMembers(Member.newBuilder()
            .setRole(Member.Role.DEFAULT)
            .setProfileKey(ByteString.copyFrom(validUserTwoPniPresentation.getProfileKeyCiphertext().serialize()))
            .setUserId(ByteString.copyFrom(validUserTwoPniPresentation.getAciCiphertext().serialize()))
            .setJoinedAtVersion(1)
            .build())
        .build());

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(signedChange.getChangeEpoch()).isEqualTo(5);
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserTwoPniPresentation.getPniCiphertext().serialize()));

    final List<PromoteMemberPendingPniAciProfileKeyAction> promoteActions =
        Actions.parseFrom(signedChange.getActions()).getPromoteMembersPendingPniAciProfileKeyList();

    assertThat(promoteActions).isNotEmpty();

    for (final PromoteMemberPendingPniAciProfileKeyAction action : promoteActions) {
      assertThat(action.getPresentation().isEmpty()).isTrue();
      assertThat(action.getUserId().isEmpty()).isFalse();
      assertThat(action.getPni().isEmpty()).isFalse();
      assertThat(action.getProfileKey().isEmpty()).isFalse();
    }

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
        new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  @Test
  public void testAcceptMemberPendingPniAciProfileKeyInvitationUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation =
        new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams())
            .createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL);

    PniCredentialPresentation validUserTwoPniPresentation =
        new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams())
            .createPniCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PNI_CREDENTIAL);

    Group group = Group.newBuilder()
        .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
        .setAccessControl(AccessControl.newBuilder()
            .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
            .setAttributes(AccessControl.AccessRequired.MEMBER))
        .setTitle(ByteString.copyFromUtf8("Some title"))
        .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
        .setVersion(0)
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.ADMINISTRATOR)
            .build())
        .addMembersPendingProfileKey(MemberPendingProfileKey.newBuilder()
            .setAddedByUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setTimestamp(System.currentTimeMillis())
            .setMember(Member.newBuilder()
                .setUserId(ByteString.copyFrom(validUserTwoPniPresentation.getPniCiphertext().serialize()))
                .setRole(Member.Role.DEFAULT)
                .build())
            .build())
        .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = Actions.newBuilder()
        .setVersion(1)
        .addPromoteMembersPendingPniAciProfileKey(Actions.PromoteMemberPendingPniAciProfileKeyAction.newBuilder()
            .setPresentation(ByteString.copyFrom(validUserTwoPniPresentation.serialize())))
        .build();

    Response response = resources.getJerseyTest()
        .target("/v1/groups/")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
        .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void testModifyMembersRole() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyMemberAccess(Actions.ModifyMembersAccessControlAction.newBuilder()
                                                                                                                        .setMembersAccess(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getAccessControl().getMembers()).isEqualTo(AccessControl.AccessRequired.ADMINISTRATOR);
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .setAccessControl(captor.getValue().getAccessControl().toBuilder().setMembers(AccessControl.AccessRequired.MEMBER))
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testModifyMembersAccessRoleUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .setModifyMemberAccess(Actions.ModifyMembersAccessControlAction.newBuilder()
                                                                                                                        .setMembersAccess(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testModifyMemberRole() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addModifyMemberRoles(Actions.ModifyMemberRoleAction.newBuilder()
                                                                                                             .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                             .setRole(Member.Role.ADMINISTRATOR).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    ArgumentCaptor<Group>       captor       = ArgumentCaptor.forClass(Group.class      );
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    assertThat(captor.getValue().getMembers(1).getRole()).isEqualTo(Member.Role.ADMINISTRATOR);
    assertThat(captor.getValue().getVersion()).isEqualTo(1);

    assertThat(captor.getValue().toBuilder()
                     .clearMembers()
                     .addMembers(captor.getValue().getMembers(0))
                     .addMembers(captor.getValue().getMembers(1).toBuilder().setRole(Member.Role.DEFAULT))
                     .setVersion(0)
                     .build()).isEqualTo(group);

    assertThat(signedChange).isEqualTo(changeCaptor.getValue());
    assertThat(Actions.parseFrom(signedChange.getActions()).getVersion()).isEqualTo(1);
    assertThat(Actions.parseFrom(signedChange.getActions()).getSourceUuid()).isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(Actions.parseFrom(signedChange.getActions()).toBuilder().clearSourceUuid().build()).isEqualTo(groupChange);

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
                                                                   new NotarySignature(signedChange.getServerSignature().toByteArray()));

  }

  @Test
  void testModifyMemberRoleUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    when(groupsManager.appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), any(GroupChange.class), any(Group.class)))
        .thenReturn(CompletableFuture.completedFuture(true));

    GroupChange.Actions groupChange = GroupChange.Actions.newBuilder()
                                                         .setVersion(1)
                                                         .addModifyMemberRoles(Actions.ModifyMemberRoleAction.newBuilder()
                                                                                                             .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                                                                                             .setRole(Member.Role.DEFAULT).build())
                                                         .build();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .method("PATCH", Entity.entity(groupChange.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(403);

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testGetGroupLogsTest() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(5)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .setJoinedAtVersion(0)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    List<GroupChangeState> expectedChanges = new LinkedList<>() {{
      add(GroupChangeState.newBuilder()
                          .setGroupChange(GroupChange.newBuilder()
                                                     .setActions(Actions.newBuilder()
                                                                        .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                                         .setTitle(ByteString.copyFromUtf8("First title"))
                                                                                                         .build())
                                                                        .build()
                                                                        .toByteString()))
                          .setGroupState(group.toBuilder().setTitle(ByteString.copyFromUtf8("First title")).build())
                          .build());

      add(GroupChangeState.newBuilder()
                          .setGroupChange(GroupChange.newBuilder()
                                                     .setActions(Actions.newBuilder()
                                                                        .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                                         .setTitle(ByteString.copyFromUtf8("Second title"))
                                                                                                         .build())
                                                                        .build()
                                                                        .toByteString())
                                                     .build())
                          .setGroupState(group.toBuilder().setTitle(ByteString.copyFromUtf8("Second title")).build())
                          .build());

      add(GroupChangeState.newBuilder()
                          .setGroupChange(GroupChange.newBuilder()
                                                     .setActions(Actions.newBuilder()
                                                                        .setModifyTitle(ModifyTitleAction.newBuilder()
                                                                                                         .setTitle(ByteString.copyFromUtf8("Some title"))
                                                                                                         .build())
                                                                        .build()
                                                                        .toByteString())
                                                     .build())
                          .setGroupState(group.toBuilder().setTitle(ByteString.copyFromUtf8("Some title")).build())
                          .build());

      String firstAvatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());

      add(GroupChangeState.newBuilder()
                          .setGroupChange(GroupChange.newBuilder()
                                                     .setActions(Actions.newBuilder()
                                                                        .setModifyAvatar(ModifyAvatarAction.newBuilder()
                                                                                                           .setAvatar(firstAvatar).build())
                                                                        .build()
                                                                        .toByteString())
                                                     .build())
                          .setGroupState(group.toBuilder().setTitle(ByteString.copyFromUtf8("Some title")).setAvatar(firstAvatar).build())
                          .build());

      String secondAvatar = avatarFor(groupPublicParams.getGroupIdentifier().serialize());

      add(GroupChangeState.newBuilder()
                          .setGroupChange(GroupChange.newBuilder()
                                                     .setActions(Actions.newBuilder()
                                                                        .setModifyAvatar(ModifyAvatarAction.newBuilder()
                                                                                                           .setAvatar(secondAvatar).build())
                                                                        .build()
                                                                        .toByteString())
                                                     .build())
                          .setGroupState(group.toBuilder().setTitle(ByteString.copyFromUtf8("Some title")).setAvatar(secondAvatar).build())
                          .build());
    }};


    when(groupsManager.getChangeRecords(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(group), isNull(), eq(false), eq(false), eq(1), eq(6)))
        .thenReturn(CompletableFuture.completedFuture(expectedChanges));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/logs/1")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();

    GroupChanges receivedChanges = GroupChanges.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    assertThat(GroupChanges.newBuilder().addAllGroupChanges(expectedChanges).build()).isEqualTo(receivedChanges);
  }

  @Test
  void testGetGroupLogsClientLimitedTest() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
        .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
        .setAccessControl(AccessControl.newBuilder()
            .setMembers(AccessControl.AccessRequired.MEMBER)
            .setAttributes(AccessControl.AccessRequired.MEMBER))
        .setTitle(ByteString.copyFromUtf8("New Title #10"))
        .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
        .setVersion(10)
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.DEFAULT)
            .setJoinedAtVersion(0)
            .build())
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.ADMINISTRATOR)
            .build())
        .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    List<GroupChangeState> expectedChanges = new ArrayList<>(5);
    for (int i = 6; i < 11; i++) {
      expectedChanges.add(generateSubjectChange(group, "New Title #" + i, i, true));
    }

    when(groupsManager.getChangeRecords(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(group), isNull(), eq(false), eq(false), eq(6), eq(10)))
        .thenReturn(CompletableFuture.completedFuture(expectedChanges.subList(0, 4)));

    Response response = resources.getJerseyTest()
        .target("/v1/groups/logs/6")
        .queryParam("limit", "4")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
        .get();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_PARTIAL_CONTENT);
    assertThat(response.getHeaderString(HttpHeaders.CONTENT_RANGE)).isEqualTo("versions 6-9/10");
    assertThat(response.hasEntity()).isTrue();

    GroupChanges receivedChanges = GroupChanges.parseFrom(response.readEntity(byte[].class));

    assertThat(GroupChanges.newBuilder().addAllGroupChanges(expectedChanges.subList(0, 4)).build()).isEqualTo(receivedChanges);
  }

  @Test
  void testGetGroupLogsClientLimitedAndChangeEpochProvidedTest() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
        .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
        .setAccessControl(AccessControl.newBuilder()
            .setMembers(AccessControl.AccessRequired.MEMBER)
            .setAttributes(AccessControl.AccessRequired.MEMBER))
        .setTitle(ByteString.copyFromUtf8("New Title #10"))
        .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
        .setVersion(10)
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.DEFAULT)
            .setJoinedAtVersion(0)
            .build())
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.ADMINISTRATOR)
            .build())
        .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    List<GroupChangeState> expectedChanges = new ArrayList<>(5);
    for (int i = 6; i < 11; i++) {
      expectedChanges.add(generateSubjectChange(group, "New Title #" + i, i, false));
    }

    when(groupsManager.getChangeRecords(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(group), eq(Integer.valueOf(0)), eq(false), eq(false), eq(6), eq(10)))
        .thenReturn(CompletableFuture.completedFuture(expectedChanges.subList(0, 4)));

    Response response = resources.getJerseyTest()
        .target("/v1/groups/logs/6")
        .queryParam("limit", "4")
        .queryParam("maxSupportedChangeEpoch", "0")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
        .get();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_PARTIAL_CONTENT);
    assertThat(response.getHeaderString(HttpHeaders.CONTENT_RANGE)).isEqualTo("versions 6-9/10");
    assertThat(response.hasEntity()).isTrue();

    GroupChanges receivedChanges = GroupChanges.parseFrom(response.readEntity(byte[].class));

    assertThat(GroupChanges.newBuilder().addAllGroupChanges(expectedChanges.subList(0, 4)).build()).isEqualTo(receivedChanges);
  }

  @Test
  void testGetGroupLogsTooManyTest() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("New Title #70"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(70)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .setJoinedAtVersion(0)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    List<GroupChangeState> expectedChanges = new ArrayList<>(65);
    for (int i = 6; i < 71; i++) {
      expectedChanges.add(generateSubjectChange(group, "New Title #" + i, i, true));
    }

    when(groupsManager.getChangeRecords(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(group), isNull(), eq(false), eq(false), eq(6), eq(70)))
        .thenReturn(CompletableFuture.completedFuture(expectedChanges.subList(0, 64)));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/logs/6")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_PARTIAL_CONTENT);
    assertThat(response.getHeaderString(HttpHeaders.CONTENT_RANGE)).isEqualTo("versions 6-69/70");
    assertThat(response.hasEntity()).isTrue();

    GroupChanges receivedChanges = GroupChanges.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    assertThat(GroupChanges.newBuilder().addAllGroupChanges(expectedChanges.subList(0, 64)).build()).isEqualTo(receivedChanges);
  }

  @Test
  void testGetGroupLogsTooOldTest() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.
            VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(5)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .setJoinedAtVersion(3)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/logs/1")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();

    verify(groupsManager).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testGetGroupJoinedAtVersion() throws IOException {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.
            VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(5)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .setJoinedAtVersion(3)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    // Verify that non-admin member has correct `joinedAtVersion`
    {
      Response response = resources.getJerseyTest()
                                   .target("/v1/groups/joined_at_version")
                                   .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                   .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                   .get();

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.hasEntity()).isTrue();

      Member member = Member.parseFrom(response.readEntity(InputStream.class).readAllBytes());
      assertThat(member.getJoinedAtVersion()).isEqualTo(3);
    }

    // Verify that admin member has correct `joinedAtVersion`
    {
      Response response = resources.getJerseyTest()
                                   .target("/v1/groups/joined_at_version")
                                   .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                   .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                   .get();

      assertThat(response.getStatus()).isEqualTo(200);

      // NOTE: since `joinedAtVersion` is 0, the protobuf encoding is just an
      // empty byte array and we have no entity here.
      assertThat(response.hasEntity()).isFalse();

      Member member = Member.parseFrom(response.readEntity(InputStream.class).readAllBytes());
      assertThat(member.getJoinedAtVersion()).isEqualTo(0);
    }

    // Verify that non-member don't get 200
    {
      Response response = resources.getJerseyTest()
                                   .target("/v1/groups/joined_at_version")
                                   .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                   .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_THREE_AUTH_CREDENTIAL))
                                   .get();

      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.hasEntity()).isFalse();
    }

    verify(groupsManager, times(3)).getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())));
    verifyNoMoreInteractions(groupsManager);
  }

  @Test
  void testGetAvatarUpload() throws IOException {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/avatar/form")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();

    AvatarUploadAttributes uploadAttributes = AvatarUploadAttributes.parseFrom(response.readEntity(InputStream.class).readAllBytes());

    assertThat(uploadAttributes.getKey()).startsWith("groups/" + Base64.getUrlEncoder().withoutPadding().encodeToString(groupPublicParams.getGroupIdentifier().serialize()));
    assertThat(uploadAttributes.getAcl()).isEqualTo("private");
    assertThat(uploadAttributes.getCredential()).isNotEmpty();
    assertThat(uploadAttributes.getDate()).isNotEmpty();
    assertThat(uploadAttributes.getSignature()).isNotEmpty();
  }

  @Test
  void testGetGroupCredentialToken() throws Exception {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.DEFAULT)
                                         .build())
                       .build();


    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    Response response = resources.getJerseyTest()
                                .target("/v1/groups/token")
                                .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
                                .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();

    byte[]                  entity     = response.readEntity(InputStream.class).readAllBytes();
    ExternalGroupCredential credential = ExternalGroupCredential.parseFrom(entity);

    assertThat(credential.getToken()).isNotBlank();
    assertThat(credential.getToken().split(":").length).isEqualTo(6);
  }

  @Test
  void testGetGroupCredentialTokenUnauthorized() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );

    Group group = Group.newBuilder()
                       .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
                       .setAccessControl(AccessControl.newBuilder()
                                                      .setMembers(AccessControl.AccessRequired.MEMBER)
                                                      .setAttributes(AccessControl.AccessRequired.MEMBER))
                       .setTitle(ByteString.copyFromUtf8("Some title"))
                       .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
                       .setVersion(0)
                       .addMembers(Member.newBuilder()
                                         .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
                                         .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .build())
                       .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/token")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.hasEntity()).isFalse();
  }

  @Test
  void testGetGroupCredentialTokenNotFound() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    Response response = resources.getJerseyTest()
                                 .target("/v1/groups/token")
                                 .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
                                 .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_TWO_AUTH_CREDENTIAL))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.hasEntity()).isFalse();
  }

  @Test
  void testGetGroupLogsAllTheParamsTest() {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();
    GroupPublicParams groupPublicParams = groupSecretParams.getPublicParams();

    ProfileKeyCredentialPresentation validUserPresentation    = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_PROFILE_CREDENTIAL    );
    ProfileKeyCredentialPresentation validUserTwoPresentation = new ClientZkProfileOperations(AuthHelper.GROUPS_SERVER_KEY.getPublicParams()).createProfileKeyCredentialPresentation(groupSecretParams, AuthHelper.VALID_USER_TWO_PROFILE_CREDENTIAL);

    Group group = Group.newBuilder()
        .setPublicKey(ByteString.copyFrom(groupPublicParams.serialize()))
        .setAccessControl(AccessControl.newBuilder()
            .setMembers(AccessControl.AccessRequired.MEMBER)
            .setAttributes(AccessControl.AccessRequired.MEMBER))
        .setTitle(ByteString.copyFromUtf8("Some Title"))
        .setAvatar(avatarFor(groupPublicParams.getGroupIdentifier().serialize()))
        .setVersion(1)
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.DEFAULT)
            .setJoinedAtVersion(0)
            .build())
        .addMembers(Member.newBuilder()
            .setUserId(ByteString.copyFrom(validUserTwoPresentation.getUuidCiphertext().serialize()))
            .setProfileKey(ByteString.copyFrom(validUserTwoPresentation.getProfileKeyCiphertext().serialize()))
            .setRole(Member.Role.ADMINISTRATOR)
            .build())
        .build();

    when(groupsManager.getGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize()))))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

    when(groupsManager.getChangeRecords(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(group), eq(Integer.valueOf(0)), eq(true), eq(true), eq(0), eq(1)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    resources.getJerseyTest()
        .target("/v1/groups/logs/0")
        .queryParam("limit", "1")
        .queryParam("maxSupportedChangeEpoch", "0")
        .queryParam("includeFirstState", "true")
        .queryParam("includeLastState", "true")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
        .get();

    verify(groupsManager).getChangeRecords(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(group), eq(Integer.valueOf(0)), eq(true), eq(true), eq(0), eq(1));
  }

  @Test
  void testLastAdminLeavesGroup() throws Exception {
    setupGroupsManagerBehaviors(group);

    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
        .setVersion(1)
        .addDeleteMembers(Actions.DeleteMemberAction.newBuilder()
            .setDeletedUserId(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize())))
        .build();

    Response response = resources.getJerseyTest()
        .target("/v1/groups/")
        .request(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
        .header("Authorization", AuthHelper.getAuthHeader(groupSecretParams, AuthHelper.VALID_USER_AUTH_CREDENTIAL))
        .method("PATCH", Entity.entity(actions.toByteArray(), ProtocolBufferMediaType.APPLICATION_PROTOBUF));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isTrue();
    assertThat(response.getMediaType().toString()).isEqualTo("application/x-protobuf");

    GroupChange signedChange = GroupChange.parseFrom(response.readEntity(InputStream.class));

    ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
    ArgumentCaptor<GroupChange> changeCaptor = ArgumentCaptor.forClass(GroupChange.class);

    verify(groupsManager).updateGroup(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), captor.capture());
    verify(groupsManager).appendChangeRecord(eq(ByteString.copyFrom(groupPublicParams.getGroupIdentifier().serialize())), eq(1), changeCaptor.capture(), any(Group.class));

    Group resultGroup = captor.getValue();
    assertThat(resultGroup.getVersion()).as("check group version").isEqualTo(1);
    assertThat(resultGroup.getMembersCount()).as("check member count").isEqualTo(1);
    assertThat(resultGroup.getMembers(0).getRole()).as("check user role").isEqualTo(Member.Role.ADMINISTRATOR);
    assertThat(resultGroup.getMembers(0).getUserId()).as("check user id").isEqualTo(group.getMembers(1).getUserId());

    assertThat(resultGroup.toBuilder()
        .clearMembers()
        .addMembers(group.getMembers(0))
        .addMembers(resultGroup.getMembers(0).toBuilder().setRole(Member.Role.DEFAULT))
        .setVersion(0)
        .build()).isEqualTo(group);

    assertThat(signedChange).as("check returned change matches the saved change").isEqualTo(changeCaptor.getValue());

    Actions resultActions = Actions.parseFrom(signedChange.getActions());
    assertThat(resultActions.getVersion()).as("check change version").isEqualTo(1);
    assertThat(resultActions.getSourceUuid()).as("check source of the change is correct").isEqualTo(ByteString.copyFrom(validUserPresentation.getUuidCiphertext().serialize()));
    assertThat(resultActions.toBuilder().clearSourceUuid().build()).as("check that the actions were modified from the input").isNotEqualTo(actions);
    assertThat(resultActions.toBuilder().clearSourceUuid().clearModifyMemberRoles().build()).as("check that the actions were modified by adding modify member roles").isEqualTo(actions);
    assertThat(resultActions.getModifyMemberRolesCount()).as("check only one modify member role action").isEqualTo(1);
    assertThat(resultActions.getModifyMemberRoles(0).getRole()).as("check setting the remaining member to admin").isEqualTo(Role.ADMINISTRATOR);
    assertThat(resultActions.getModifyMemberRoles(0).getUserId()).as("check user id promoted to admin was the remaining group member").isEqualTo(group.getMembers(1).getUserId());

    AuthHelper.GROUPS_SERVER_KEY.getPublicParams().verifySignature(signedChange.getActions().toByteArray(),
        new NotarySignature(signedChange.getServerSignature().toByteArray()));
  }

  private GroupChangeState generateSubjectChange(final Group group, final String newTitle, final int version, final boolean includeGroupState) {
    GroupChangeState.Builder groupChangeStateBuilder = GroupChangeState.newBuilder()
        .setGroupChange(GroupChange.newBuilder()
            .setActions(Actions.newBuilder()
                .setVersion(version)
                .setModifyTitle(ModifyTitleAction.newBuilder()
                    .setTitle(ByteString.copyFromUtf8(newTitle)))
                .build()
                .toByteString()));
    if (includeGroupState) {
      groupChangeStateBuilder.setGroupState(group.toBuilder()
          .setVersion(version)
          .setTitle(ByteString.copyFromUtf8(newTitle)));
    }
    return groupChangeStateBuilder.build();
  }
}
