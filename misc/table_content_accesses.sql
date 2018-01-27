CREATE TABLE `content_data_accesses` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `module_id` int(11) DEFAULT NULL,
  `treatment_access_id` int(11) DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  `content_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq` (`module_id`,`treatment_access_id`,`content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
