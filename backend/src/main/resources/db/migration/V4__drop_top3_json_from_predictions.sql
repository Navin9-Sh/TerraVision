-- The API now returns only the top prediction; the top-3 breakdown is no longer
-- stored or read anywhere. predicted_class and confidence (already separate columns)
-- keep the top result for every row, old and new.
ALTER TABLE predictions DROP COLUMN top3_json;
