package com.example.bulletinboard.service;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.repository.PostRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class PostService {
    private final PostRepository postRepository;

    //コンストラクタでRepositoryクラスをインジェクション
    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    // 投稿一覧を取得するメソッド
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

   // 投稿をIDで取得するメソッド
    public Optional<Post> findById(Long id) {
        return postRepository.findById(id);
    }

   // 新規投稿を保存するメソッド
    public Post savePost(Post post) {
        return postRepository.save(post);
    }

   //IDで投稿を削除するメソッド
    public void deleteById(Long id) {
        postRepository.deleteById(id);
    }

}