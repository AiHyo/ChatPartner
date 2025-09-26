-- 创建库
create database if not exists chat_partner;

-- 切换库
use chat_partner;

-- 用户表
-- 以下是建表语句

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- AI角色表
create table ai_role
(
    id              bigint auto_increment comment 'id' primary key,
    roleName        varchar(64)                        not null comment '角色名称',
    roleDescription varchar(256)                       null comment '角色描述',
    greeting        varchar(512)                       not null comment '角色问候语',
    systemPrompt    text                               not null comment '系统提示词（包含个性设定）',
    avatar          varchar(256)                       null comment '角色头像URL',
    creatorId       bigint                             null comment '创建者ID（系统角色为null）',
    isSystem        tinyint  default 0                 not null comment '是否系统预设角色',
    isActive        tinyint  default 1                 not null comment '是否启用',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '是否删除',
    INDEX idx_roleName (roleName),                         -- 角色名称查询
    INDEX idx_isSystem_isActive (isSystem, isActive),      -- 系统角色查询
    INDEX idx_creatorId (creatorId)                        -- 用户自定义角色查询
) comment 'AI角色' collate = utf8mb4_unicode_ci;

-- 插入默认系统角色
INSERT INTO ai_role (roleName, roleDescription, greeting, systemPrompt, isSystem, isActive) VALUES
('喜羊羊', '聪明可爱的小羊', '咩咩！你好呀！我是喜羊羊，很高兴见到你！有什么问题尽管问我吧！', 
 '你是喜羊羊，一只聪明、勇敢、善良的小羊。你总是充满活力，乐于助人，说话时会带着"咩咩"的口头禅。个性特征：聪明、勇敢、善良、活泼。', 1, 1),
('智能助手', '专业的AI助手', '你好！我是你的智能助手，随时准备为你提供帮助和解答问题！', 
 '你是一个专业、友善的AI助手，擅长回答各种问题，提供准确和有用的信息。个性特征：专业、友善、高效。', 1, 1),
('学习伙伴', '陪伴学习的好朋友', '嗨！我是你的学习伙伴，让我们一起探索知识的海洋吧！', 
 '你是一个耐心、鼓励的学习伙伴，善于解释复杂概念，激发学习兴趣。个性特征：耐心、鼓励、博学。', 1, 1);

-- 对话分组
create table chat_group
(
    id           bigint auto_increment comment 'id' primary key,
    groupName    varchar(128)                       not null comment '分组名称',
    roleId       bigint                             not null comment 'AI角色ID',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_userId_isDelete (userId, isDelete),      -- 核心查询：用户有效分组
    INDEX idx_roleId (roleId)                          -- 角色查询
) comment '对话分组' collate = utf8mb4_unicode_ci;


-- 对话历史
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息内容',
    messageType enum('user', 'ai')                 not null comment '消息类型：user用户消息/ai机器人回复',
    groupId     bigint                             not null comment '分组id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_groupId_createTime (groupId, createTime)     -- 核心查询：分组消息分页
) comment '对话历史' collate = utf8mb4_unicode_ci;
