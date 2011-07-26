/*
 * Copyright (C) 2010 Pavel Stastny
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.kramerius.security.impl;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import cz.incad.kramerius.security.Role;
import cz.incad.kramerius.security.User;
import cz.incad.kramerius.security.UserManager;
import cz.incad.kramerius.security.database.InitSecurityDatabase;
import cz.incad.kramerius.security.database.SecurityDatabaseUtils;
import cz.incad.kramerius.security.utils.SecurityDBUtils;
import cz.incad.kramerius.utils.database.JDBCCommand;
import cz.incad.kramerius.utils.database.JDBCQueryTemplate;
import cz.incad.kramerius.utils.database.JDBCTransactionTemplate;
import cz.incad.kramerius.utils.database.JDBCUpdateTemplate;

public class DatabaseUserManager implements UserManager{

    static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(DatabaseUserManager.class.getName());
    
    @Inject
    @Named("kramerius4")
    Provider<Connection> provider;
    
    @Inject
    Provider<HttpServletRequest> requestProvider;

    
    
    
    @Override
    @InitSecurityDatabase
    public User validateUser(final String loginName, final String passwd) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findUser").toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    returnsList.add(SecurityDBUtils.createUser(rs));
                    return true;
            }
        }.executeQuery(sql, loginName, passwd);
        return (users != null) && (!users.isEmpty()) ? users.get(0) : null;
    }

    public Provider<Connection> getProvider() {
        return provider;
    }

    public void setProvider(Provider<Connection> provider) {
        this.provider = provider;
    }

    @Override
    @InitSecurityDatabase
    public Role[] findGroupsForGivenUser(int user_id) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findAllGroupsByUserId").toString();
        List<Role> users= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
        }.executeQuery(sql, user_id);
        return (users != null) ? (Role[]) users.toArray(new Role[users.size()]) : new Role[0];
    }

    @Override
    @InitSecurityDatabase
    public User findUser(int user_id) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findUserByUserId").toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User user = SecurityDBUtils.createUser(rs);
                    returnsList.add(user);
                    return true;
            }
        }.executeQuery(sql, user_id);
        return (users != null) && (!users.isEmpty()) ? users.get(0) : null;
    }

    @Override
    @InitSecurityDatabase
    public Role findGroup(int group_id) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findGroupByGroupId").toString();
        List<Role> groups= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
        }.executeQuery(sql, group_id);
        return (groups != null) && (!groups.isEmpty()) ? groups.get(0) : null;
    }

    @Override
    @InitSecurityDatabase
    public Role findCommonUsersGroup() {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findCommonUsersGroup").toString();
        List<Role> groups= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
        }.executeQuery(sql);
        return (groups != null) && (!groups.isEmpty()) ? groups.get(0) : null;
    }

    
    
    
    @Override
    @InitSecurityDatabase
    public Role[] findGroupsWhichIAdministrate(int[] grpIds) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findGroupsWhichAdministrate");
        template.setAttribute("grps", grpIds);
        String sql = template.toString();
        List<Role> groups= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
            
        }.executeQuery(sql);
        return (Role[]) groups.toArray(new Role[groups.size()]);
    }

    @Override
    @InitSecurityDatabase
    public Role findGlobalAdminGroup() {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findGlobalAdminsGroup").toString();
        List<Role> groups= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
        }.executeQuery(sql);
        return (groups != null) && (!groups.isEmpty()) ? groups.get(0) : null;
    }

    @Override
    @InitSecurityDatabase
    public Role findGroupByName(String gname) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findGroupByGname").toString();
        List<Role> groups= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
        }.executeQuery(sql, gname);
        return (groups != null) && (!groups.isEmpty()) ? groups.get(0) : null;
    }

    @Override
    @InitSecurityDatabase
    public User findUserByLoginName(String loginName) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findUserByLoginName").toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User user = SecurityDBUtils.createUser(rs);
                    returnsList.add(user);
                    return true;
            }
        }.executeQuery(sql, loginName);
        return (users != null) && (!users.isEmpty()) ? users.get(0) : null;
    }

    @Override
    @InitSecurityDatabase
    public User[] findUserByPrefix(String prefix) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findUserByPrefix").toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User user = SecurityDBUtils.createUser(rs);
                    returnsList.add(user);
                    return true;
            }
        }.executeQuery(sql, prefix+"%");
        return (User[]) users.toArray(new User[users.size()]);
    }

    @Override
    @InitSecurityDatabase
    public User[] findUserByPrefixForGroups(String prefix, int[] grpIds) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findUserByPrefixForGroups");
        template.setAttribute("grps", grpIds);
        String sql = template.toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User user = SecurityDBUtils.createUser(rs);
                    returnsList.add(user);
                    return true;
            }
        }.executeQuery(sql, prefix+"%");
        return (User[]) users.toArray(new User[users.size()]);
    }

    @Override
    @InitSecurityDatabase
    public Role[] findGroupByPrefix(String prefix) {
        String sql = SecurityDatabaseUtils.stGroup().getInstanceOf("findGroupByPrefix").toString();
        List<Role> users= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role group = SecurityDBUtils.createGroup(rs);
                    returnsList.add(group);
                    return true;
            }
        }.executeQuery(sql, prefix+"%");
        return (Role[]) users.toArray(new Role[users.size()]);
    }

    @Override
    @InitSecurityDatabase
    public Role[] findGroupByPrefixForGroups(String prefix,int[] grpIds) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findGroupByPrefixForGroups");
        template.setAttribute("grps", grpIds);
        String sql = template.toString();
        List<Role> users= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role group = SecurityDBUtils.createGroup(rs);
                    returnsList.add(group);
                    return true;
            }
        }.executeQuery(sql, prefix+"%");
        return (Role[]) users.toArray(new Role[users.size()]);
    }

    @Override
    public void saveNewPassword(int userId, String pswd) throws SQLException {
        JDBCUpdateTemplate updateTemplate = new JDBCUpdateTemplate(this.provider.get());
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("updatePassword");
        updateTemplate.executeUpdate(template.toString(), pswd, userId);
    }

    @InitSecurityDatabase
    public User[] findAllUsers(String prefix) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findAllUsers");
        template.setAttribute("prefix", prefix.trim().equals("") ?  null : prefix);
        String sql = template.toString();
        List<User> users= null;
        if (prefix.trim().equals("")) {
            users = new JDBCQueryTemplate<User>(this.provider.get()){
                @Override
                public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                        User user = SecurityDBUtils.createUser(rs);
                        returnsList.add(user);
                        return true;
                }
            }.executeQuery(sql);
        } else {
            users = new JDBCQueryTemplate<User>(this.provider.get()){
                @Override
                public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                        User user = SecurityDBUtils.createUser(rs);
                        returnsList.add(user);
                        return true;
                }
            }.executeQuery(sql, prefix+"%");
        }
        return (User[]) users.toArray(new User[users.size()]);
    }

    
    @Override
    @InitSecurityDatabase
    public User[] findAllUsers(int[] grpIds) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findAllUsersForGroups");
        template.setAttribute("grps", grpIds);
        template.setAttribute("prefix", null);
        String sql = template.toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User user = SecurityDBUtils.createUser(rs);
                    returnsList.add(user);
                    return true;
            }
        }.executeQuery(sql, grpIds);
        return (User[]) users.toArray(new User[users.size()]);
    }

    
    
    @Override
    @InitSecurityDatabase
    public User[] findAllUsers(int[] grpIds, String prefix) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findAllUsersForGroups");
        template.setAttribute("grps", grpIds);
        template.setAttribute("prefix", prefix);
        String sql = template.toString();
        List<User> users= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User user = SecurityDBUtils.createUser(rs);
                    returnsList.add(user);
                    return true;
            }
        }.executeQuery(sql, grpIds, prefix+"%");
        return (User[]) users.toArray(new User[users.size()]);
    }

    @Override
    @InitSecurityDatabase
    public Role[] findAllGroups(int[] grpIds, String prefix) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findAllGroupsForGroups");
        template.setAttribute("grps", grpIds);
        template.setAttribute("prefix", prefix== null ? "" : prefix);
        String sql = template.toString();
        List<Role> grps= new JDBCQueryTemplate<Role>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                    Role grp = SecurityDBUtils.createGroup(rs);
                    returnsList.add(grp);
                    return true;
            }
        }.executeQuery(sql, grpIds, prefix+"%");
        return (Role[]) grps.toArray(new Role[grps.size()]);
    }

    
    @InitSecurityDatabase
    public Role[] findAllGroups(String prefix) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findAllGroups");
        template.setAttribute("prefix", prefix.trim().equals("") ?  null : prefix);
        String sql = template.toString();
        List<Role> grps= null;
        if (prefix.trim().equals("")) {
            grps= new JDBCQueryTemplate<Role>(this.provider.get()){
                @Override
                public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                        Role grp = SecurityDBUtils.createGroup(rs);
                        returnsList.add(grp);
                        return true;
                }
            }.executeQuery(sql);
        } else {
            grps= new JDBCQueryTemplate<Role>(this.provider.get()){
                @Override
                public boolean handleRow(ResultSet rs, List<Role> returnsList) throws SQLException {
                        Role grp = SecurityDBUtils.createGroup(rs);
                        returnsList.add(grp);
                        return true;
                }
            }.executeQuery(sql, prefix+"%");
        }
        return (Role[]) grps.toArray(new Role[grps.size()]);
    }

    @Override
    @InitSecurityDatabase
    public User[] findUsersForGivenGroup(int groupId) {
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("findUsersForGivenGroup");
        String sql = template.toString();
        List<User> usrs= new JDBCQueryTemplate<User>(this.provider.get()){
            @Override
            public boolean handleRow(ResultSet rs, List<User> returnsList) throws SQLException {
                    User usr = SecurityDBUtils.createUser(rs);
                    returnsList.add(usr);
                    return true;
            }
        }.executeQuery(sql, groupId);
        return (User[]) usrs.toArray(new User[usrs.size()]);
    }

    
    

    @Override
    public void insertGroup(Role role) throws SQLException{
        StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("insertRole");
        template.setAttribute("role", role);
        JDBCUpdateTemplate jdbcTemplate = new JDBCUpdateTemplate(this.provider.get(), true);
        String sql = template.toString();
        LOGGER.fine(sql);
        jdbcTemplate.executeUpdate(sql);
    }

    @Override
    public void removeGroup(Role role) throws SQLException {
            StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("deleteRole");
            template.setAttribute("role", role);
            JDBCUpdateTemplate jdbcTemplate = new JDBCUpdateTemplate(this.provider.get(), true);
            String sql = template.toString();
            LOGGER.fine(sql);
            jdbcTemplate.executeUpdate(sql);
    }

    @Override
    public void editGroup(Role role) throws SQLException {
            StringTemplate template = SecurityDatabaseUtils.stGroup().getInstanceOf("updateRole");
            template.setAttribute("role", role);
            JDBCUpdateTemplate jdbcTemplate = new JDBCUpdateTemplate(this.provider.get(), true);
            String sql = template.toString();
            LOGGER.fine(sql);
            jdbcTemplate.executeUpdate(sql);
    }

    @Override
    public boolean isLoggedUser(User user) {
        if (this.requestProvider.get().getRemoteUser() != null) {
            if (!user.getLoginname().equals(NOT_LOGGED_USER)) {
                return true;
            } else return false;
        } else return false;
    }

    @Override
    public void registerLoggedUser(final User user, final String loggedUserKey) throws SQLException {
        final StringTemplateGroup stGroup = SecurityDatabaseUtils.stGroup();
        final Connection connection = this.provider.get();
        
        List<JDBCCommand> commands = new ArrayList<JDBCCommand>();
        commands.add(new JDBCCommand() {
            
            @Override
            public Object executeJDBCCommand() throws SQLException {
                StringTemplate template = stGroup.getInstanceOf("registerLoggedUser");
                template.setAttribute("user", user);
                template.setAttribute("userkey", loggedUserKey);

                JDBCUpdateTemplate update = new JDBCUpdateTemplate(connection, false);
                Integer retVal = new Integer(update.executeUpdate(template.toString()));
                return retVal;
            }
        });
        
        Role[] roles = user.getGroups();
        for (final Role role : roles) {
            commands.add(new JDBCCommand() {
                
                @Override
                public Object executeJDBCCommand() throws SQLException {
                    Integer loggedUserID = (Integer) getPreviousResult();
                    
                    StringTemplate template = stGroup.getInstanceOf("registerLoggedUserUpdateRoles");
                    template.setAttribute("loggeduserid", user.getId());
                    template.setAttribute("roleid", role.getId());
                    
                    JDBCUpdateTemplate update = new JDBCUpdateTemplate(connection, false);
                    update.executeUpdate(template.toString());
                    
                    return loggedUserID;
                }
            });
            
        }
        
        // update in transaction
        new JDBCTransactionTemplate(connection, true).
            updateWithTransaction(commands.toArray(new JDBCCommand[commands.size()]));
    }
    
}
