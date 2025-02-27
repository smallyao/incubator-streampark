/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.streampark.console.system.service.impl;

import org.apache.streampark.common.util.AssertUtils;
import org.apache.streampark.console.base.domain.RestRequest;
import org.apache.streampark.console.base.exception.ApiAlertException;
import org.apache.streampark.console.base.mybatis.pager.MybatisPager;
import org.apache.streampark.console.system.entity.Member;
import org.apache.streampark.console.system.entity.Team;
import org.apache.streampark.console.system.entity.User;
import org.apache.streampark.console.system.mapper.MemberMapper;
import org.apache.streampark.console.system.service.MemberService;
import org.apache.streampark.console.system.service.RoleService;
import org.apache.streampark.console.system.service.TeamService;
import org.apache.streampark.console.system.service.UserService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true, rollbackFor = Exception.class)
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements MemberService {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private TeamService teamService;

    @Override
    @Transactional
    public void removeByRoleIds(String[] roleIds) {
        this.lambdaUpdate().in(Member::getRoleId, Arrays.asList(roleIds)).remove();
    }

    @Override
    @Transactional
    public void removeByUserId(Long userId) {
        this.lambdaUpdate().eq(Member::getUserId, userId).remove();
    }

    @Override
    public void removeByTeamId(Long teamId) {
        this.lambdaUpdate().eq(Member::getTeamId, teamId).remove();
    }

    @Override
    public IPage<Member> getPage(Member member, RestRequest request) {
        ApiAlertException.throwIfNull(member.getTeamId(), "The team id is required.");
        Page<Member> page = MybatisPager.getPage(request);
        return baseMapper.selectPage(page, member);
    }

    @Override
    public List<User> listUsersNotInTeam(Long teamId) {
        return baseMapper.selectUsersNotInTeam(teamId);
    }

    @Override
    public List<Team> listTeamsByUserId(Long userId) {
        return teamService.listByUserId(userId);
    }

    @Override
    public Member getByTeamIdUserName(Long teamId, String userName) {
        User user = userService.getByUsername(userName);
        if (user == null) {
            return null;
        }
        return findByUserId(teamId, user.getUserId());
    }

    private Member findByUserId(Long teamId, Long userId) {
        ApiAlertException.throwIfNull(teamId, "The team id is required.");
        return this.lambdaQuery()
            .eq(Member::getTeamId, teamId)
            .eq(Member::getUserId, userId)
            .one();
    }

    @Override
    public List<Long> listUserIdsByRoleId(Long roleId) {
        List<Member> memberList = this.lambdaQuery().eq(Member::getRoleId, roleId).list();
        return memberList.stream().map(Member::getUserId).collect(Collectors.toList());
    }

    @Override
    public void createMember(Member member) {
        User user = userService.getByUsername(member.getUserName());
        ApiAlertException.throwIfNull(user, "The username [%s] not found", member.getUserName());

        ApiAlertException.throwIfNull(
            roleService.getById(member.getRoleId()), "The roleId [%s] not found", member.getRoleId());
        Team team = teamService.getById(member.getTeamId());
        ApiAlertException.throwIfNull(team, "The teamId [%s] not found", member.getTeamId());
        ApiAlertException.throwIfNotNull(
            findByUserId(member.getTeamId(), user.getUserId()),
            "The user [%s] has been added the team [%s], please don't add it again.",
            member.getUserName(),
            team.getTeamName());

        member.setId(null);
        member.setUserId(user.getUserId());

        this.save(member);
    }

    @Override
    public void remove(Long id) {
        Member member = this.getById(id);
        ApiAlertException.throwIfNull(member, "The member [id=%s] not found", id);
        this.removeById(member);
        userService.clearLastTeam(member.getUserId(), member.getTeamId());
    }

    @Override
    public void updateMember(Member member) {
        Member oldMember = this.getById(member.getId());
        ApiAlertException.throwIfNull(oldMember, "The member [id=%s] not found", member.getId());
        AssertUtils.state(
            oldMember.getTeamId().equals(member.getTeamId()), "Team id cannot be changed.");
        AssertUtils.state(
            oldMember.getUserId().equals(member.getUserId()), "User id cannot be changed.");
        ApiAlertException.throwIfNull(
            roleService.getById(member.getRoleId()), "The roleId [%s] not found", member.getRoleId());
        oldMember.setRoleId(member.getRoleId());
        updateById(oldMember);
    }
}
