VERSION   := $(shell date +%Y%m%d)
PKG_NAME  := hify-$(VERSION)
PKG_DIR   := dist/$(PKG_NAME)
PKG_FILE  := $(PKG_NAME).tar.gz

# 确保 JAVA_HOME 指向 JDK 17（兼容 macOS jenv 未初始化的场景）
JAVA_HOME := $(shell /usr/libexec/java_home -v 17 2>/dev/null || echo "$$JAVA_HOME")
export JAVA_HOME

MVN := mvn
NPM := npm

.PHONY: start stop restart build build-backend build-frontend \
        clean clean-backend clean-frontend package help

# ── 默认目标 ─────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "  make start          启动前后端"
	@echo "  make stop           优雅停止前后端"
	@echo "  make restart        重启前后端"
	@echo "  make build          构建后端 + 前端"
	@echo "  make clean          清理所有构建产物"
	@echo "  make package        构建并打包为 $(PKG_FILE)"
	@echo ""

# ── 服务管理 ─────────────────────────────────────────────────────────────────
start:
	./start.sh

stop:
	./stop.sh

restart: stop start

# ── 构建 ─────────────────────────────────────────────────────────────────────
build: build-backend build-frontend

build-backend:
	@echo "[build] 构建后端..."
	$(MVN) clean install -DskipTests -q
	@echo "[build] 后端构建完成 -> hify-app/target/hify-app-*.jar"

build-frontend:
	@echo "[build] 构建前端..."
	cd hify-web && $(NPM) run build
	@echo "[build] 前端构建完成 -> hify-web/dist/"

# ── 清理 ─────────────────────────────────────────────────────────────────────
clean: clean-backend clean-frontend
	rm -rf dist/ logs/

clean-backend:
	@echo "[clean] 清理后端..."
	$(MVN) clean -q

clean-frontend:
	@echo "[clean] 清理前端..."
	rm -rf hify-web/dist

# ── 打包 ─────────────────────────────────────────────────────────────────────
package: build
	@echo "[package] 打包为 $(PKG_FILE)..."

	@# 准备打包目录
	rm -rf $(PKG_DIR)
	mkdir -p $(PKG_DIR)/frontend $(PKG_DIR)/docker/mysql $(PKG_DIR)/docker/postgres

	@# 后端 fat jar
	cp hify-app/target/hify-app-*.jar $(PKG_DIR)/hify-app.jar

	@# 前端静态资源
	cp -r hify-web/dist/. $(PKG_DIR)/frontend/

	@# 配置与运行脚本
	cp hify-app/src/main/resources/application.yml $(PKG_DIR)/
	cp start.sh stop.sh Makefile                   $(PKG_DIR)/
	chmod +x $(PKG_DIR)/start.sh $(PKG_DIR)/stop.sh

	@# Docker 相关
	cp Dockerfile docker-compose.yml               $(PKG_DIR)/
	cp docker/mysql/init.sql   $(PKG_DIR)/docker/mysql/
	cp docker/postgres/init.sql $(PKG_DIR)/docker/postgres/

	@# .env 模板（去掉真实密码，只保留 key 占位符）
	sed 's/=.*/=/' .env > $(PKG_DIR)/.env.example

	@# 打 tar.gz
	mkdir -p dist
	tar -czf dist/$(PKG_FILE) -C dist $(PKG_NAME)
	rm -rf $(PKG_DIR)

	@echo "[package] 完成 -> dist/$(PKG_FILE)"
	@echo ""
	@ls -lh dist/$(PKG_FILE)
