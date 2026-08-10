'use strict';
document.addEventListener('DOMContentLoaded', () => {

    // 画面内の「like-btn」クラスを持つすべてのボタンを取得してイベントを登録
    document.querySelectorAll('.like-btn').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.preventDefault();

            // クリック時にCSRFトークン情報を取得
            const tokenElement = document.querySelector('meta[name="_csrf"]');
            const headerElement = document.querySelector('meta[name="_csrf_header"]');

            if (!tokenElement || !headerElement) {
                console.error('エラー: CSRFメタタグが見つかりません。');
                return;
            }

            const token = tokenElement.getAttribute('content');
            const header = headerElement.getAttribute('content');
            const postId = btn.getAttribute('data-post-id');

            try {
                // バッククォート (`) を使用して URL 内で postId を展開
                const response = await fetch(`/posts/${postId}/like`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        [header]: token
                    }
                });

                if (!response.ok) throw new Error(`通信エラー (ステータス: ${response.status})`);

                const data = await response.json();

                const countSpan = btn.querySelector('.like-count');
                if (countSpan) countSpan.textContent = data.count;

                if (data.liked) {
                    btn.classList.remove('btn-outline-danger');
                    btn.classList.add('btn-danger');
                } else {
                    btn.classList.remove('btn-danger');
                    btn.classList.add('btn-outline-danger');
                }

            } catch (error) {
                console.error('いいね処理のエラー:', error);
            }
        });
    });

});