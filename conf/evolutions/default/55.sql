# DEFAULT SCHEMA

# --- !Ups

UPDATE postings SET title = 'calendar', content = '<p>Welcome at the advent calender made by the IEEE Student Branch Passau!</p>' WHERE id = 2 AND lang = 'en';
UPDATE postings SET title = 'status', content = 'Nothing new' WHERE id = 3 AND lang = 'en';

# --- !Downs

UPDATE postings SET title = 'status', content = 'Nothing new' WHERE id = 2 AND lang = 'en';
UPDATE postings SET title = 'calendar', content = '<p>Welcome at the advent calender made by the IEEE Student Branch Passau!</p>' WHERE id = 3 AND lang = 'en';
