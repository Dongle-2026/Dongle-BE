-- 기사 대표 이미지(원문 og:image URL). 이미지 파일은 저장하지 않고 원문 주소만 보관한다.
ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(1000);
