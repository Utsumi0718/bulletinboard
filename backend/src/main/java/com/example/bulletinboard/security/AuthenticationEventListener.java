package com.example.bulletinboard.security;

import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.service.CustomUserDetailsService;

/**
 * ログインの成功・失敗イベントを監視し、失敗カウントのリセットを行うクラス
*/

@Component
public class AuthenticationEventListener {

   private final CustomUserDetailsService userDetailsService;
   private final UserRepository userRepository;

  //CustomUserDetailsService・UserRepositoryをインジェクション
   public AuthenticationEventListener(CustomUserDetailsService userDetailsService, UserRepository userRepository){
     this.userDetailsService = userDetailsService;
     this.userRepository = userRepository;
   }

   /**
    * ログイン成功時の処理
    */
   @EventListener
   public void onAuthenticationSuccess(AuthenticationSuccessEvent event){
     String username = event.getAuthentication().getName();
     //成功した場合は失敗カウントを０にする
     userDetailsService.resetFailedAttempts(username);
   }

   /**
    * ログイン失敗時（パスワードの誤り等）の処理
    */

   @EventListener
   public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
     String username = event.getAuthentication().getName();
     Optional<User> userOptional = userRepository.findByUsername(username);

     //存在するユーザーの場合のみ失敗カウントを加算
     if(userOptional.isPresent()){
       User user = userOptional.get();
       //ユーザーがロックされていない場合のみカウント加算処理を実行
       if(user.isAccountNonLocked()){
          userDetailsService.increaseFailedAttempts(user);
       }
     }
   }

}
