/*
Navicat MySQL Data Transfer

Source Server         : 本地数据库
Source Server Version : 50624
Source Host           : 127.0.0.1:3306
Source Database       : kl-demo

Target Server Type    : MYSQL
Target Server Version : 50624
File Encoding         : 65001

Date: 2019-07-16 18:02:44
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for person
-- ----------------------------
DROP TABLE IF EXISTS `person`;
CREATE TABLE `person` (
  `id` int(255) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `age` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of person
-- ----------------------------
INSERT INTO `person` VALUES ('1', 'kl', '22');
INSERT INTO `person` VALUES ('2', 'ckl', '33');
INSERT INTO `person` VALUES ('3', 'kkk', '66');
INSERT INTO `person` VALUES ('4', 's', '2');
INSERT INTO `person` VALUES ('5', '陈某某', '100');
INSERT INTO `person` VALUES ('6', '陈某某', '100');
