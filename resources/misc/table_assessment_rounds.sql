CREATE TABLE `assessment_rounds` (
  `Id` int(11) NOT NULL AUTO_INCREMENT,
  `RoundId` int(11) DEFAULT NULL,
  `Time` timestamp NULL DEFAULT NULL,
  `UserId` int(11) DEFAULT NULL,
  `BatchId` int(11) DEFAULT NULL,
  `Step` int(11) DEFAULT NULL,
  `InstrumentId` int(11) DEFAULT NULL,
  `Texts` text,
  `AdministrationId` int(11) DEFAULT NULL,
  `Completed` timestamp NULL DEFAULT NULL,
  `MustShowTexts` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`Id`),
  UNIQUE KEY `RoundIdStep` (`RoundId`,`Step`)
)