package com.example.bulletinboard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.bulletinboard.form.ContactForm;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.repository.ContactRepository;




/**
 * [クラスの役割]
 * お問い合わせ機能の一連の画面遷移（入力・確認・完了）
 * メール送信処理（JavaMailSender）を制御するController
 */

@Controller
@RequestMapping("/contact")
public class ContactController{

    private final JavaMailSender mailSender;
    private final ContactRepository
     contactRepository; // 追記

    //application.propertiesから送信元（お問い合わせ用メールアドレス）を取得
    @Value( "${spring.mail.username}")
    private String mailForm;


    // コンストラクタ注入に contactRepository を追加
    public ContactController(JavaMailSender mailSender, ContactRepository contactRepository) {
        this.mailSender = mailSender;
        this.contactRepository = contactRepository;
    }


    //お問い合わせ入力画面の表示
  @GetMapping
    public String index(Model model) {
        if (!model.containsAttribute("contactForm")) {
            model.addAttribute("contactForm", new ContactForm());
        }
        return "contact/index";
    }


    //入力内容の確認画面を表示
    @PostMapping("/confirm")
    public String confirm(@Validated @ModelAttribute("contactForm")
                          ContactForm contactForm,
                          BindingResult result) {
        //入力エラーがある場合は入力画面へ戻す
        if(result.hasErrors()){
         return "contact/index";
        }

        return "contact/confirm";

    }

    //メール送信処理の実行と完了画面の表示
    @PostMapping("/send")
    public String send(@Validated @ModelAttribute("contactForm")
                          ContactForm form,
                          BindingResult result,
                          Model model){
      // 画面側のバリデーションをすり抜けた不正リクエストに対する、サーバー側の最終チェック
    if (result.hasErrors()) {
     return "contact/index";
    }

    //追記:DBにお問い合わせ情報を保存する処理を追加
    Contact contact = new Contact();
        contact.setName(form.getName());
        contact.setEmail(form.getEmail());
        contact.setSubject(form.getSubject());
        contact.setMessage(form.getMessage());
        contactRepository.save(contact);

     //メールオブジェクトの作成
     SimpleMailMessage message = new SimpleMailMessage();
     message.setFrom(mailForm);
     message.setTo(mailForm);//お問い合わせ受信アドレス（管理者宛て）
     message.setReplyTo(form.getEmail());//返信先をお問い合わせしたユーザーのアドレスに設定
     message.setSubject("[お問い合わせ]" + form.getSubject());
     message.setText("お名前" + form.getName() + "\n" +
                    "メールアドレス" + form.getEmail() + "\n\n" +
                    "[お問い合わせ内容]\n" + form.getMessage());

         //メール送信の実行
          mailSender.send(message);

          return "contact/complete";

    }


}