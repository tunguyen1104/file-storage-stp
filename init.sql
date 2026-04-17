BEGIN;

-- Xóa dữ liệu cũ
TRUNCATE TABLE users_roles CASCADE;
TRUNCATE TABLE order_item CASCADE;
TRUNCATE TABLE orders CASCADE;
TRUNCATE TABLE cart_item CASCADE;
TRUNCATE TABLE cart CASCADE;
TRUNCATE TABLE lesson CASCADE;
TRUNCATE TABLE chapter CASCADE;
TRUNCATE TABLE courses CASCADE;
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE role CASCADE;

-- ========== ROLE ==========
INSERT INTO role (id, name, created_at, updated_at)
VALUES 
  (1, 'ROLE_ADMIN', NOW(), NOW()),
  (2, 'ROLE_TEACHER', NOW(), NOW()),
  (3, 'ROLE_STUDENT', NOW(), NOW());

-- ========== USERS ==========
-- password đã mã hóa BCrypt cho "123456"
INSERT INTO users (id, full_name, email, password, enabled, avatar, created_at, updated_at)
VALUES
  (1, 'Admin User', 'admin@example.com', '$2a$10$Vh6rQcfOIsUZe2mu9Yg1Ku9hX9F1Xvuvyk7sItQFkYoIYQwFJlviS', true, 'default-avatar.png', NOW(), NOW()),
  (2, 'Teacher User', 'teacher@example.com', '$2a$10$Vh6rQcfOIsUZe2mu9Yg1Ku9hX9F1Xvuvyk7sItQFkYoIYQwFJlviS', true, 'default-avatar.png', NOW(), NOW()),
  (3, 'Student User', 'student@example.com', '$2a$10$Vh6rQcfOIsUZe2mu9Yg1Ku9hX9F1Xvuvyk7sItQFkYoIYQwFJlviS', true, 'default-avatar.png', NOW(), NOW());

-- ========== USERS_ROLES ==========
INSERT INTO users_roles (user_id, roles_id)
VALUES
  (1, 1),
  (2, 2),
  (3, 3);

-- ========== COURSES ==========
INSERT INTO courses (id, title, description, image, price, category, duration, owner, created_at, updated_at)
VALUES
  (1, 'Spring Boot Mastery', 'Khóa học Spring Boot từ A-Z', 'springboot.png', 49.99, 'PROGRAMMING', 600, 2, NOW(), NOW()),
  (2, 'Docker for Beginners', 'Học Docker cơ bản', 'docker.png', 29.99, 'DEVOPS', 300, 2, NOW(), NOW());

-- ========== CHAPTER ==========
INSERT INTO chapter (id, title, course_id, order_index, created_at, updated_at)
VALUES
  (1, 'Giới thiệu Spring Boot', 1, 1, NOW(), NOW()),
  (2, 'Cấu hình Docker cơ bản', 2, 1, NOW(), NOW());

-- ========== LESSON ==========
INSERT INTO lesson (id, title, description, image_object_key, resource_object_key, lesson_type, duration_minutes, course_id, chapter_id, order_index, created_at, updated_at)
VALUES
  (1, 'Cài đặt môi trường', 'Hướng dẫn cài đặt Java & IntelliJ', NULL, 'resources/lesson1.mp4', 'VIDEO', 15, 1, 1, 1, NOW(), NOW()),
  (2, 'Hello World Spring Boot', 'Viết ứng dụng đầu tiên', NULL, 'resources/lesson2.mp4', 'VIDEO', 20, 1, 1, 2, NOW(), NOW()),
  (3, 'Docker Run', 'Cách chạy container đầu tiên', NULL, 'resources/lesson3.pdf', 'DOCUMENT', 10, 2, 2, 1, NOW(), NOW());

-- ========== CART ==========
INSERT INTO cart (id, user_id, checked_out, created_at, updated_at)
VALUES
  (1, 3, false, NOW(), NOW());

-- ========== CART_ITEM ==========
INSERT INTO cart_item (id, cart_id, course_id, quantity, created_at, updated_at)
VALUES
  (1, 1, 1, 1, NOW(), NOW()),
  (2, 1, 2, 1, NOW(), NOW());

-- ========== ORDERS ==========
INSERT INTO orders (id, buyer_id, total_amount, status, created_at, updated_at)
VALUES
  (1, 3, 79.98, 'PAID', NOW(), NOW());

-- ========== ORDER_ITEM ==========
INSERT INTO order_item (id, order_id, course_id, price_at_purchase, created_at, updated_at)
VALUES
  (1, 1, 1, 49.99, NOW(), NOW()),
  (2, 1, 2, 29.99, NOW(), NOW());

COMMIT;

