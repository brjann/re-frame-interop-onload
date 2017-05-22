CREATE TABLE `assessment_rounds` (
  `Id` int(11) NOT NULL AUTO_INCREMENT,
  `RoundId` int(11) DEFAULT NULL,
  `Time` datetime DEFAULT NULL,
  `UserId` int(11) DEFAULT NULL,
  `BatchId` int(11) DEFAULT NULL,
  `Step` int(11) DEFAULT NULL,
  `Text` text,
  `InstrumentId` int(11) DEFAULT NULL,
  `AdministrationId` int(11) DEFAULT NULL,
  `AnswersId` int(11) DEFAULT NULL,
  `Completed` int(11) DEFAULT NULL,
  PRIMARY KEY (`Id`),
  UNIQUE KEY `RoundIdStep` (`RoundId`,`Step`)
);