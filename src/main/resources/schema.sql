-- ═══════════════════════════════════════════════════════════════════════════
-- Database Schema for User Entity
-- ═══════════════════════════════════════════════════════════════════════════

-- Users table
CREATE TABLE IF NOT EXISTS users (
    user_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50) NOT NULL UNIQUE,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Roles table (for reference)
CREATE TABLE IF NOT EXISTS roles (
    role_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name   VARCHAR(50) NOT NULL UNIQUE
);

-- User-Role junction table (many-to-many)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_first_name ON users(first_name);
CREATE INDEX IF NOT EXISTS idx_users_last_name ON users(last_name);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- Sample Data
-- ═══════════════════════════════════════════════════════════════════════════

-- Insert sample roles
INSERT INTO roles (role_id, role_name) VALUES (1, 'ADMIN');
INSERT INTO roles (role_id, role_name) VALUES (2, 'USER');
INSERT INTO roles (role_id, role_name) VALUES (3, 'MODERATOR');

-- Insert sample users
INSERT INTO users (user_id, username, first_name, last_name) VALUES 
    (1, 'jdoe', 'John', 'Doe'),
    (2, 'jsmith', 'Jane', 'Smith'),
    (3, 'bwilson', 'Bob', 'Wilson'),
    (4, 'ajohnson', 'Alice', 'Johnson'),
    (5, 'mgarcia', 'Maria', 'Garcia');

-- Assign roles to users
INSERT INTO user_roles (user_id, role_id) VALUES 
    (1, 1), (1, 2),           -- John: ADMIN, USER
    (2, 2),                   -- Jane: USER
    (3, 2), (3, 3),           -- Bob: USER, MODERATOR
    (4, 1), (4, 2), (4, 3),   -- Alice: ADMIN, USER, MODERATOR
    (5, 2);                   -- Maria: USER

-- ═══════════════════════════════════════════════════════════════════════════
-- Deal Entity Schema (Query Entity Pattern Demo)
-- ═══════════════════════════════════════════════════════════════════════════

-- Deals table
CREATE TABLE IF NOT EXISTS deals (
    deal_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
    deal_name   VARCHAR(200) NOT NULL,
    analyst_id  BIGINT,
    deal_status VARCHAR(50) DEFAULT 'ACTIVE',
    deal_amount DECIMAL(15,2),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analyst_id) REFERENCES users(user_id)
);

-- Programs table (One-to-Many with Deals)
CREATE TABLE IF NOT EXISTS programs (
    program_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    program_name VARCHAR(200) NOT NULL,
    deal_id      BIGINT NOT NULL,
    program_type VARCHAR(50),
    budget       DECIMAL(15,2),
    FOREIGN KEY (deal_id) REFERENCES deals(deal_id) ON DELETE CASCADE
);

-- Contracts table (One-to-Many with Programs)
CREATE TABLE IF NOT EXISTS contracts (
    contract_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_name VARCHAR(200) NOT NULL,
    program_id    BIGINT NOT NULL,
    FOREIGN KEY (program_id) REFERENCES programs(program_id) ON DELETE CASCADE
);

-- Indexes for deals
CREATE INDEX IF NOT EXISTS idx_deals_analyst_id ON deals(analyst_id);
CREATE INDEX IF NOT EXISTS idx_deals_deal_name ON deals(deal_name);
CREATE INDEX IF NOT EXISTS idx_deals_deal_status ON deals(deal_status);
CREATE INDEX IF NOT EXISTS idx_programs_deal_id ON programs(deal_id);
CREATE INDEX IF NOT EXISTS idx_contracts_program_id ON contracts(program_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- Sample Deal Data
-- ═══════════════════════════════════════════════════════════════════════════

-- Insert sample deals (analyst_id references users table)
INSERT INTO deals (deal_id, deal_name, analyst_id, deal_status, deal_amount) VALUES
    (1, 'Project Alpha', 1, 'ACTIVE', 1500000.00),      -- John Doe is analyst
    (2, 'Project Beta', 1, 'ACTIVE', 2500000.00),       -- John Doe is analyst
    (3, 'Project Gamma', 2, 'PENDING', 800000.00),      -- Jane Smith is analyst
    (4, 'Project Delta', 3, 'ACTIVE', 3200000.00),      -- Bob Wilson is analyst
    (5, 'Project Epsilon', 4, 'CLOSED', 500000.00),     -- Alice Johnson is analyst
    (6, 'Project Zeta', 2, 'ACTIVE', 1800000.00),       -- Jane Smith is analyst
    (7, 'Project Eta', NULL, 'DRAFT', 0.00);            -- No analyst assigned

-- Insert sample programs (linked to deals)
INSERT INTO programs (program_id, program_name, deal_id, program_type, budget) VALUES
    -- Project Alpha programs
    (1, 'Alpha Phase 1', 1, 'DEVELOPMENT', 500000.00),
    (2, 'Alpha Phase 2', 1, 'TESTING', 300000.00),
    (3, 'Alpha Deployment', 1, 'DEPLOYMENT', 200000.00),
    
    -- Project Beta programs
    (4, 'Beta Research', 2, 'RESEARCH', 800000.00),
    (5, 'Beta Development', 2, 'DEVELOPMENT', 1200000.00),
    
    -- Project Gamma programs
    (6, 'Gamma Pilot', 3, 'PILOT', 400000.00),
    
    -- Project Delta programs
    (7, 'Delta Phase 1', 4, 'DEVELOPMENT', 1000000.00),
    (8, 'Delta Phase 2', 4, 'DEVELOPMENT', 1200000.00),
    (9, 'Delta Testing', 4, 'TESTING', 500000.00),
    (10, 'Delta Launch', 4, 'DEPLOYMENT', 500000.00),
    
    -- Project Epsilon programs
    (11, 'Epsilon Maintenance', 5, 'MAINTENANCE', 500000.00),
    
    -- Project Zeta programs
    (12, 'Zeta Analysis', 6, 'RESEARCH', 600000.00),
    (13, 'Zeta Prototype', 6, 'DEVELOPMENT', 800000.00);

-- Insert sample contracts (linked to programs)
INSERT INTO contracts (contract_id, contract_name, program_id) VALUES
    -- Alpha Phase 1 contracts
    (1, 'Alpha Phase 1 - Dev Contract', 1),
    (2, 'Alpha Phase 1 - Support Contract', 1),
    -- Alpha Phase 2 contracts
    (3, 'Alpha Phase 2 - QA Contract', 2),
    -- Beta Research contracts
    (4, 'Beta Research - Consulting', 4),
    -- Delta Phase 1 contracts
    (5, 'Delta Phase 1 - Implementation', 7),
    (6, 'Delta Phase 1 - Licensing', 7);

-- Note: Project Eta (deal_id=7) has NO programs - tests LEFT JOIN behavior
