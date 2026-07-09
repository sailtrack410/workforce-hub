UPDATE `kb_space` s
JOIN `kb_ragflow_setting` g ON g.`id` = 1
SET s.`embedding_model_id` = COALESCE(s.`embedding_model_id`, g.`embedding_model_id`),
    s.`embedding_dimension` = COALESCE(s.`embedding_dimension`, g.`embedding_dimension`),
    s.`chunk_size` = COALESCE(s.`chunk_size`, g.`chunk_size`, 800),
    s.`chunk_overlap` = COALESCE(s.`chunk_overlap`, g.`chunk_overlap`, 100),
    s.`retrieval_top_k` = COALESCE(s.`retrieval_top_k`, g.`retrieval_top_k`, 5)
WHERE s.`is_deleted` = 0;
