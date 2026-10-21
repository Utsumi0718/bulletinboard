package com.example.bulletinboard.service;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@Import({PrivacyAccessService.class,PrivacyCrypto.class,PrivacyRetentionService.class,WithdrawalFollowupService.class})
public class PrivacyModule { }
