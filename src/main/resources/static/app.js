(() => {
  "use strict";

  const STORAGE = {
    token: "xiaoyu.admin.token",
    user: "xiaoyu.admin.user"
  };
  const VIEW_TITLES = {
    overview: "经营概览",
    orders: "订单管理",
    products: "商品管理",
    users: "用户管理",
    addresses: "客户地址",
    settings: "系统设置",
    ai: "AI 运营助手"
  };

  const state = {
    token: localStorage.getItem(STORAGE.token) || "",
    user: readJson(STORAGE.user, null),
    preview: false,
    busy: false,
    mode: "checking",
    currentView: "overview",
    overviewLoaded: false,
    ordersLoaded: false,
    productsLoaded: false,
    usersLoaded: false,
    addressesLoaded: false,
    settingsLoaded: false,
    orderStatus: "",
    productKeyword: "",
    productStatus: "",
    productStockThreshold: "",
    userKeyword: "",
    addressKeyword: "",
    addressUserId: "",
    pages: {
      orders: { current: 1, size: 10, pages: 0, total: 0 },
      products: { current: 1, size: 8, pages: 0, total: 0 },
      users: { current: 1, size: 8, pages: 0, total: 0 },
      addresses: { current: 1, size: 8, pages: 0, total: 0 }
    },
    currentOrders: [],
    currentProducts: [],
    currentUsers: [],
    currentAddresses: []
  };

  const demo = createDemoData();
  const el = {};
  document.querySelectorAll("[id]").forEach((node) => {
    el[node.id] = node;
  });
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  init();

  function init() {
    bindEvents();
    updateDate();
    loadPublicSettings();
    const params = new URLSearchParams(location.search);
    if (params.get("preview") === "1") {
      enterPreview();
    } else if (state.token && state.user) {
      restoreSession();
    }
  }

  function bindEvents() {
    el["login-form"].addEventListener("submit", handleLogin);
    el["preview-button"].addEventListener("click", enterPreview);
    el["reveal-password"].addEventListener("click", togglePassword);
    el["logout-button"].addEventListener("click", logout);
    el["mode-chip"].addEventListener("click", connectStatus);
    el["refresh-status-button"].addEventListener("click", connectStatus);
    el["menu-button"].addEventListener("click", openSidebar);
    el["sidebar-close"].addEventListener("click", closeSidebar);
    el["sidebar-backdrop"].addEventListener("click", closeSidebar);
    el["topbar-ai-button"].addEventListener("click", () => switchView("ai"));
    el["floating-ai-button"].addEventListener("click", () => switchView("ai"));
    el["new-chat-button"].addEventListener("click", startNewAiConversation);
    el["clear-chat-button"].addEventListener("click", startNewAiConversation);
    el["assistant-form"].addEventListener("submit", submitQuestion);
    el["assistant-input"].addEventListener("input", resizeInput);
    el["assistant-input"].addEventListener("keydown", handleComposerKeydown);
    el["messages"].addEventListener("click", handleMessageClick);

    el["refresh-orders-button"].addEventListener("click", () => loadOrders(state.pages.orders.current));
    el["refresh-products-button"].addEventListener("click", () => loadProducts(state.pages.products.current));
    el["refresh-users-button"].addEventListener("click", () => loadUsers(state.pages.users.current));
    el["refresh-addresses-button"].addEventListener("click", () => loadAddresses(state.pages.addresses.current));
    el["reload-settings-button"].addEventListener("click", loadSettings);
    el["settings-form"].addEventListener("submit", saveSettings);
    el["product-search-form"].addEventListener("submit", handleProductSearch);
    el["product-status-filters"].addEventListener("click", handleProductFilter);
    el["user-search-form"].addEventListener("submit", handleUserSearch);
    el["address-search-form"].addEventListener("submit", handleAddressSearch);
    el["address-user-filter-form"].addEventListener("submit", handleAddressUserFilter);
    el["add-product-button"].addEventListener("click", () => openProductModal());
    el["product-form"].addEventListener("submit", saveProduct);
    el["close-product-modal"].addEventListener("click", closeProductModal);
    el["cancel-product-button"].addEventListener("click", closeProductModal);
    el["product-modal-backdrop"].addEventListener("click", (event) => {
      if (event.target === el["product-modal-backdrop"]) closeProductModal();
    });
    el["close-detail-button"].addEventListener("click", closeDetailDrawer);
    el["detail-backdrop"].addEventListener("click", closeDetailDrawer);

    el["order-status-filters"].addEventListener("click", (event) => {
      const chip = event.target.closest("[data-status]");
      if (!chip) return;
      state.orderStatus = chip.dataset.status;
      $$(".filter-chip", el["order-status-filters"]).forEach((item) => item.classList.toggle("active", item === chip));
      loadOrders(1);
    });

    document.addEventListener("click", (event) => {
      const nav = event.target.closest("[data-view]");
      if (nav) {
        event.preventDefault();
        switchView(nav.dataset.view);
        return;
      }
      const prompt = event.target.closest("[data-prompt]");
      if (prompt) {
        askFromAnywhere(prompt.dataset.prompt);
        return;
      }
      const detail = event.target.closest("[data-order-detail]");
      if (detail) {
        openOrderDetail(Number(detail.dataset.orderDetail));
        return;
      }
      const deliver = event.target.closest("[data-order-deliver]");
      if (deliver) {
        deliverOrder(Number(deliver.dataset.orderDeliver));
        return;
      }
      const editProduct = event.target.closest("[data-product-edit]");
      if (editProduct) {
        openProductModal(Number(editProduct.dataset.productEdit));
        return;
      }
      const toggleProduct = event.target.closest("[data-product-toggle]");
      if (toggleProduct) {
        toggleProductStatus(Number(toggleProduct.dataset.productToggle), Number(toggleProduct.dataset.nextStatus));
        return;
      }
      const deleteProduct = event.target.closest("[data-product-delete]");
      if (deleteProduct) {
        deleteProductItem(Number(deleteProduct.dataset.productDelete));
        return;
      }
      const toggleUser = event.target.closest("[data-user-toggle]");
      if (toggleUser) {
        toggleUserStatus(Number(toggleUser.dataset.userToggle), Number(toggleUser.dataset.nextStatus));
        return;
      }
      const userAddresses = event.target.closest("[data-user-addresses]");
      if (userAddresses) {
        state.addressUserId = userAddresses.dataset.userAddresses;
        el["address-user-id-input"].value = state.addressUserId;
        switchView("addresses");
        loadAddresses(1);
        return;
      }
      const addressDetail = event.target.closest("[data-address-detail]");
      if (addressDetail) {
        openAddressDetail(Number(addressDetail.dataset.addressDetail));
        return;
      }
      const pageButton = event.target.closest("[data-page-target]");
      if (pageButton) {
        loadPage(pageButton.dataset.pageTarget, Number(pageButton.dataset.page));
      }
    });
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoginLoading(true);
    el["login-error"].textContent = "";
    try {
      const response = await api("/user/login", {
        method: "POST",
        auth: false,
        body: {
          username: el.username.value.trim(),
          password: el.password.value
        }
      });
      if (response.code !== "200") throw new Error(response.msg || "登录失败");
      if (Number(response.data.role) !== 1) throw new Error("当前账号不是管理员");
      state.preview = false;
      state.token = response.data.token;
      state.user = response.data;
      localStorage.setItem(STORAGE.token, state.token);
      localStorage.setItem(STORAGE.user, JSON.stringify(state.user));
      await enterDashboard();
    } catch (error) {
      el["login-error"].textContent = error.message || "无法连接后端服务";
    } finally {
      setLoginLoading(false);
    }
  }

  async function restoreSession() {
    try {
      const response = await api("/admin/ai/status");
      if (response.code === "401" || response.code === "403") {
        clearSession();
        return;
      }
      await enterDashboard();
    } catch (error) {
      clearSession();
      el["login-error"].textContent = "后端连接失败，请确认服务已启动";
    }
  }

  async function enterDashboard() {
    el["login-view"].classList.add("is-hidden");
    el["dashboard-view"].classList.remove("is-hidden");
    renderUser();
    await connectStatus();
    switchView(hashView() || "overview");
  }

  async function enterPreview() {
    state.preview = true;
    state.token = "";
    state.user = { username: "demo-admin", nickname: "演示管理员", role: 1 };
    el["login-view"].classList.add("is-hidden");
    el["dashboard-view"].classList.remove("is-hidden");
    renderUser();
    setMode({ mode: "preview", configured: false, description: "当前展示前端演示数据，未读取真实业务数据。" });
    switchView(hashView() || "overview");
  }

  async function loadPublicSettings() {
    try {
      const response = await api("/settings/public", { auth: false });
      if (response.code === "200" && response.data.systemName) {
        applySystemName(response.data.systemName);
      }
    } catch (error) {
      applySystemName("不汐汐");
    }
  }

  function applySystemName(name) {
    const safeName = String(name || "不汐汐").trim() || "不汐汐";
    $$("[data-system-name]").forEach((node) => { node.textContent = safeName; });
    document.title = `${safeName} · 订单与 AI 运营台`;
  }

  async function loadSettings() {
    state.settingsLoaded = true;
    try {
      let settings;
      if (state.preview) {
        settings = {
          systemName: "不汐汐",
          supportPhone: "400-800-1234",
          paymentTimeoutMinutes: 30,
          autoCancelEnabled: true,
          inventoryWarningThreshold: 10,
          aiEnabled: true,
          aiBaseUrl: "https://api.openai.com/v1",
          aiModel: "gpt-4.1-mini",
          aiTimeoutSeconds: 60,
          aiApiKeyConfigured: false
        };
      } else {
        const response = await api("/admin/settings");
        ensureOk(response);
        settings = response.data;
      }
      populateSettings(settings);
    } catch (error) {
      state.settingsLoaded = false;
      showToast(error.message || "设置加载失败", true);
    }
  }

  function populateSettings(settings) {
    el["setting-system-name"].value = settings.systemName || "不汐汐";
    el["setting-support-phone"].value = settings.supportPhone || "";
    el["setting-payment-timeout"].value = settings.paymentTimeoutMinutes ?? 30;
    el["setting-stock-threshold"].value = settings.inventoryWarningThreshold ?? 10;
    el["setting-auto-cancel"].checked = settings.autoCancelEnabled !== false;
    el["setting-ai-enabled"].checked = settings.aiEnabled !== false;
    el["setting-ai-base-url"].value = settings.aiBaseUrl || "https://api.openai.com/v1";
    el["setting-ai-model"].value = settings.aiModel || "gpt-4.1-mini";
    el["setting-ai-timeout"].value = settings.aiTimeoutSeconds ?? 60;
    el["setting-ai-api-key"].value = "";
    el["setting-clear-ai-key"].checked = false;
    el["ai-key-status"].textContent = settings.aiApiKeyConfigured ? "API Key 已配置" : "未配置 API Key";
    el["settings-updated-at"].textContent = settings.updateTime ? `更新于 ${formatDateTime(settings.updateTime)}` : "";
  }

  async function saveSettings(event) {
    event.preventDefault();
    const button = el["save-settings-button"];
    button.disabled = true;
    try {
      const payload = {
        systemName: el["setting-system-name"].value.trim(),
        supportPhone: el["setting-support-phone"].value.trim(),
        paymentTimeoutMinutes: Number(el["setting-payment-timeout"].value),
        autoCancelEnabled: el["setting-auto-cancel"].checked,
        inventoryWarningThreshold: Number(el["setting-stock-threshold"].value),
        aiEnabled: el["setting-ai-enabled"].checked,
        aiBaseUrl: el["setting-ai-base-url"].value.trim(),
        aiModel: el["setting-ai-model"].value.trim(),
        aiTimeoutSeconds: Number(el["setting-ai-timeout"].value),
        clearAiApiKey: el["setting-clear-ai-key"].checked
      };
      const apiKey = el["setting-ai-api-key"].value.trim();
      if (apiKey) payload.aiApiKey = apiKey;

      if (state.preview) {
        applySystemName(payload.systemName);
        el["ai-key-status"].textContent = payload.clearAiApiKey ? "未配置 API Key" : apiKey ? "API Key 已配置" : el["ai-key-status"].textContent;
        el["settings-updated-at"].textContent = `更新于 ${currentTime()}`;
      } else {
        const response = await api("/admin/settings", { method: "PUT", body: payload });
        ensureOk(response);
        populateSettings(response.data);
        applySystemName(response.data.systemName);
        await connectStatus();
      }
      el["setting-ai-api-key"].value = "";
      el["setting-clear-ai-key"].checked = false;
      showToast("设置已保存");
    } catch (error) {
      showToast(error.message || "设置保存失败", true);
    } finally {
      button.disabled = false;
    }
  }

  async function connectStatus() {
    if (state.preview) {
      setMode({ mode: "preview", configured: false, description: "当前展示前端演示数据，未读取真实业务数据。" });
      return;
    }
    setCheckingMode();
    try {
      const response = await api("/admin/ai/status");
      if (response.code === "401" || response.code === "403") {
        logout();
        return;
      }
      if (response.code !== "200") throw new Error(response.msg || "状态检查失败");
      setMode(response.data);
    } catch (error) {
      setMode({ mode: "offline", configured: false, description: "无法连接后端服务，请确认 Spring Boot 已启动。" });
      showToast(error.message || "后端连接失败", true);
    }
  }

  function switchView(view, updateUrl = true) {
    if (!VIEW_TITLES[view]) view = "overview";
    state.currentView = view;
    $$(".app-view").forEach((node) => node.classList.toggle("is-hidden", node.id !== `view-${view}`));
    $$(".nav-item[data-view]").forEach((node) => node.classList.toggle("active", node.dataset.view === view));
    el["breadcrumb-current"].textContent = VIEW_TITLES[view];
    el["floating-ai-button"].classList.toggle("is-hidden", view === "ai");
    closeSidebar();
    if (updateUrl) history.replaceState(null, "", `#${view}`);

    if (view === "overview" && !state.overviewLoaded) loadOverview();
    if (view === "orders" && !state.ordersLoaded) loadOrders(1);
    if (view === "products" && !state.productsLoaded) loadProducts(1);
    if (view === "users" && !state.usersLoaded) loadUsers(1);
    if (view === "addresses" && !state.addressesLoaded) loadAddresses(1);
    if (view === "settings" && !state.settingsLoaded) loadSettings();
    if (view === "ai" && !el["messages"].children.length) addWelcomeBlock();
  }

  function hashView() {
    const value = location.hash.replace("#", "");
    return VIEW_TITLES[value] ? value : "";
  }

  async function loadOverview() {
    state.overviewLoaded = true;
    showLoading(el["overview-orders"], "正在读取订单…");
    showLoading(el["overview-stock"], "正在读取库存…");
    try {
      let overviewData;
      let recentOrders;
      if (state.preview) {
        overviewData = demo.overview;
        recentOrders = demo.orders.slice(0, 5);
      } else {
        const [overviewResponse, orderResponse] = await Promise.all([
          api("/admin/ai/overview"),
          api("/admin/order/page?pageNum=1&pageSize=5")
        ]);
        ensureOk(overviewResponse);
        ensureOk(orderResponse);
        overviewData = overviewResponse.data;
        recentOrders = orderResponse.data.records || [];
      }
      applyOverviewMetrics(overviewData);
      renderOverviewOrders(recentOrders);
      renderOverviewStock(overviewData.lowStock?.goods || overviewData.lowStock?.records || []);
    } catch (error) {
      state.overviewLoaded = false;
      el["overview-orders"].innerHTML = emptyState("读取订单失败", error.message);
      el["overview-stock"].innerHTML = emptyState("读取库存失败", error.message);
      showToast(error.message || "概览加载失败", true);
    }
  }

  function applyOverviewMetrics(payload) {
    const overview = payload.overview || payload;
    el["metric-revenue"].textContent = money(overview.todayTotalAmount);
    el["metric-revenue-note"].textContent = `${number(overview.todayOrderCount)} 笔今日成交`;
    el["metric-pending"].textContent = `${number(overview.pendingShipmentCount)} 笔`;
    el["metric-unpaid"].textContent = `${number(overview.pendingPaymentCount)} 笔`;
    el["metric-lowstock"].textContent = `${number(overview.lowStockGoodsCount)} 件`;
    el["sidebar-pending"].textContent = number(overview.pendingShipmentCount);
  }

  function renderOverviewOrders(orders) {
    if (!orders.length) {
      el["overview-orders"].innerHTML = emptyState("暂无订单", "新订单会显示在这里");
      return;
    }
    el["overview-orders"].innerHTML = orderTable(orders, true);
  }

  function renderOverviewStock(goods) {
    if (!goods.length) {
      el["overview-stock"].innerHTML = emptyState("库存健康", "当前没有不高于 10 件的在售商品");
      return;
    }
    el["overview-stock"].innerHTML = `<div class="stock-list">${goods.slice(0, 6).map((item) => `
      <div class="stock-row">
        <div><strong>${esc(item.goodsName)}</strong><small>销量 ${number(item.sales)} · ${money(item.price)}</small></div>
        <span class="stock-number">库存 ${number(item.stock)}</span>
      </div>`).join("")}</div>`;
  }

  async function loadOrders(page = 1) {
    state.ordersLoaded = true;
    showLoading(el["orders-table"], "正在读取订单…");
    try {
      let pageData;
      if (state.preview) {
        const filtered = state.orderStatus === ""
          ? demo.orders
          : demo.orders.filter((order) => String(order.status) === String(state.orderStatus));
        pageData = paginate(filtered, page, state.pages.orders.size);
      } else {
        const query = new URLSearchParams({ pageNum: String(page), pageSize: String(state.pages.orders.size) });
        if (state.orderStatus !== "") query.set("status", state.orderStatus);
        const response = await api(`/admin/order/page?${query}`);
        ensureOk(response);
        pageData = response.data;
      }
      state.currentOrders = pageData.records || [];
      state.pages.orders = {
        current: pageData.current || page,
        size: pageData.size || state.pages.orders.size,
        pages: pageData.pages || 0,
        total: pageData.total || 0
      };
      el["orders-table"].innerHTML = state.currentOrders.length
        ? orderTable(state.currentOrders, false)
        : emptyState("没有符合条件的订单", "可以切换状态筛选后再试");
      renderPagination("orders", el["orders-pagination"]);
    } catch (error) {
      state.ordersLoaded = false;
      el["orders-table"].innerHTML = emptyState("订单加载失败", error.message);
      showToast(error.message || "订单加载失败", true);
    }
  }

  function orderTable(orders, compact) {
    return `<table class="data-table admin-table">
      <thead><tr><th>订单号</th><th>状态</th><th>实付金额</th><th>下单时间</th>${compact ? "" : "<th>用户 ID</th><th>操作</th>"}</tr></thead>
      <tbody>${orders.map((order) => `
        <tr>
          <td><button class="order-link" data-order-detail="${order.id}">${shortOrderNo(order.orderNo)}</button></td>
          <td><span class="status-badge status-${order.status}">${esc(order.statusName || statusName(order.status))}</span></td>
          <td class="money-cell">${money(order.payAmount)}</td>
          <td>${formatDateTime(order.createTime)}</td>
          ${compact ? "" : `<td class="muted-cell">${esc(order.userId || "—")}</td><td class="action-cell">
            <button class="table-action" data-order-detail="${order.id}">详情</button>
            ${Number(order.status) === 1 ? `<button class="table-action primary-text" data-order-deliver="${order.id}">发货</button>` : ""}
          </td>`}
        </tr>`).join("")}</tbody>
    </table>`;
  }

  async function openOrderDetail(orderId) {
    let order;
    try {
      if (state.preview) {
        order = demo.orders.find((item) => Number(item.id) === Number(orderId));
      } else {
        const response = await api(`/admin/order/detail/${orderId}`);
        ensureOk(response);
        order = response.data;
      }
      if (!order) throw new Error("订单不存在");
      el["detail-title"].textContent = order.orderNo || `订单 ${order.id}`;
      el["order-detail-content"].innerHTML = orderDetailHtml(order);
      el["order-detail-drawer"].classList.add("open");
      el["detail-backdrop"].classList.add("open");
    } catch (error) {
      showToast(error.message || "订单详情加载失败", true);
    }
  }

  function orderDetailHtml(order) {
    const items = order.itemList || order.itemsData || [];
    return `<div class="detail-status-row">
        <span class="status-badge status-${order.status}">${esc(order.statusName || statusName(order.status))}</span>
        <strong>${money(order.payAmount)}</strong>
      </div>
      <div class="detail-section"><h3>收货信息</h3><dl class="detail-list">
        <div><dt>收件人</dt><dd>${esc(order.addressName || "—")}</dd></div>
        <div><dt>手机号</dt><dd>${esc(order.addressPhone || "—")}</dd></div>
        <div><dt>地址</dt><dd>${esc(order.addressDetail || "—")}</dd></div>
      </dl></div>
      <div class="detail-section"><h3>商品明细</h3>
        <div class="detail-items">${items.map((item) => `<div><span>${esc(item.goodsName)} × ${number(item.buyNum)}</span><strong>${money(Number(item.goodsPrice) * Number(item.buyNum))}</strong></div>`).join("") || '<p class="empty-evidence">暂无明细</p>'}</div>
      </div>
      <div class="detail-section"><h3>订单信息</h3><dl class="detail-list">
        <div><dt>订单号</dt><dd>${esc(order.orderNo)}</dd></div>
        <div><dt>创建时间</dt><dd>${formatDateTime(order.createTime)}</dd></div>
        <div><dt>支付时间</dt><dd>${formatDateTime(order.payTime)}</dd></div>
        <div><dt>发货时间</dt><dd>${formatDateTime(order.deliveryTime)}</dd></div>
      </dl></div>
      ${Number(order.status) === 1 ? `<button class="primary-button detail-deliver" data-order-deliver="${order.id}">确认发货</button>` : ""}`;
  }

  async function deliverOrder(orderId) {
    if (!window.confirm("确认将该订单标记为已发货？")) return;
    try {
      if (state.preview) {
        const order = demo.orders.find((item) => Number(item.id) === Number(orderId));
        if (order) order.status = 2;
      } else {
        const response = await api(`/admin/order/deliver/${orderId}`, { method: "PUT" });
        ensureOk(response);
      }
      closeDetailDrawer();
      showToast("订单已发货");
      await Promise.all([loadOrders(state.pages.orders.current), refreshOverviewQuietly()]);
    } catch (error) {
      showToast(error.message || "发货失败", true);
    }
  }

  async function loadProducts(page = 1, keyword = state.productKeyword) {
    state.productsLoaded = true;
    state.productKeyword = keyword;
    showLoading(el["products-table"], "正在读取商品…");
    try {
      let pageData;
      if (state.preview) {
        const filtered = demo.products.filter((item) => {
          const matchesKeyword = !keyword || item.goodsName.toLowerCase().includes(keyword.toLowerCase());
          const matchesStatus = state.productStatus === "" || String(item.status) === String(state.productStatus);
          const matchesStock = state.productStockThreshold === "" || Number(item.stock) <= Number(state.productStockThreshold);
          return matchesKeyword && matchesStatus && matchesStock;
        });
        pageData = paginate(filtered, page, state.pages.products.size);
        updateProductStatsFromDemo();
      } else {
        const body = {
          pageNum: page,
          pageSize: state.pages.products.size,
          goodsName: keyword
        };
        if (state.productStatus !== "") body.status = Number(state.productStatus);
        if (state.productStockThreshold !== "") body.stockThreshold = Number(state.productStockThreshold);
        const [response] = await Promise.all([
          api("/admin/page", { method: "POST", body }),
          loadProductStats()
        ]);
        if (response.code) ensureOk(response);
        pageData = response;
      }
      state.currentProducts = pageData.records || [];
      state.pages.products = {
        current: pageData.current || page,
        size: pageData.size || state.pages.products.size,
        pages: pageData.pages || 0,
        total: pageData.total || 0
      };
      el["products-table"].innerHTML = state.currentProducts.length
        ? productTable(state.currentProducts)
        : emptyState("没有找到商品", "尝试更换搜索词或新增商品");
      renderPagination("products", el["products-pagination"]);
    } catch (error) {
      state.productsLoaded = false;
      el["products-table"].innerHTML = emptyState("商品加载失败", error.message);
      showToast(error.message || "商品加载失败", true);
    }
  }

  function productTable(products) {
    return `<table class="data-table admin-table product-table">
      <thead><tr><th>商品</th><th>价格</th><th>库存</th><th>销量</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>${products.map((item) => `<tr>
        <td><div class="product-cell"><span class="product-thumb">${item.pic_url ? `<img src="${escAttr(item.pic_url)}" alt="">` : esc((item.goodsName || "品").slice(0, 1))}</span><div><strong>${esc(item.goodsName)}</strong><small>ID ${esc(item.id)}</small></div></div></td>
        <td class="money-cell">${money(item.price)}</td>
        <td><span class="${Number(item.stock) <= 10 ? "low-stock-text" : ""}">${number(item.stock)}</span></td>
        <td>${number(item.sales)}</td>
        <td><span class="status-badge ${Number(item.status) === 1 ? "status-3" : "status-4"}">${Number(item.status) === 1 ? "上架" : "下架"}</span></td>
        <td class="action-cell"><button class="table-action" data-product-edit="${item.id}">编辑</button><button class="table-action primary-text" data-product-toggle="${item.id}" data-next-status="${Number(item.status) === 1 ? 0 : 1}">${Number(item.status) === 1 ? "下架" : "上架"}</button>${Number(item.status) === 0 ? `<button class="table-action danger-text" data-product-delete="${item.id}">删除</button>` : ""}</td>
      </tr>`).join("")}</tbody>
    </table>`;
  }

  async function loadProductStats() {
    try {
      const response = await api("/admin/goods/statistics");
      if (response.code !== "200") return;
      updateProductStats(response.data);
    } catch (error) {
      // Product page remains usable when only statistics fail.
    }
  }

  function updateProductStats(stats) {
    el["product-total"].textContent = number(stats.total);
    el["product-active"].textContent = number(stats.active);
    el["product-off"].textContent = number(stats.offShelf);
    el["product-low"].textContent = number(stats.lowStock);
  }

  function updateProductStatsFromDemo() {
    updateProductStats({
      total: demo.products.length,
      active: demo.products.filter((item) => Number(item.status) === 1).length,
      offShelf: demo.products.filter((item) => Number(item.status) === 0).length,
      lowStock: demo.products.filter((item) => Number(item.status) === 1 && Number(item.stock) <= 10).length
    });
  }

  function openProductModal(productId) {
    const product = productId ? state.currentProducts.find((item) => Number(item.id) === Number(productId)) : null;
    el["product-modal-title"].textContent = product ? "编辑商品" : "新增商品";
    el["product-id"].value = product?.id || "";
    el["product-version"].value = product?.version ?? "";
    el["product-name"].value = product?.goodsName || "";
    el["product-price"].value = product?.price ?? "";
    el["product-stock"].value = product?.stock ?? 0;
    el["product-pic"].value = product?.pic_url || "";
    el["product-status"].value = String(product?.status ?? 1);
    el["product-form-error"].textContent = "";
    el["product-modal-backdrop"].classList.add("open");
  }

  function closeProductModal() {
    el["product-modal-backdrop"].classList.remove("open");
  }

  async function saveProduct(event) {
    event.preventDefault();
    const id = el["product-id"].value;
    const payload = {
      goodsName: el["product-name"].value.trim(),
      price: Number(el["product-price"].value),
      stock: Number(el["product-stock"].value),
      pic_url: el["product-pic"].value.trim(),
      status: Number(el["product-status"].value)
    };
    if (id) {
      payload.id = Number(id);
      payload.version = Number(el["product-version"].value);
    }
    el["save-product-button"].disabled = true;
    try {
      if (state.preview) {
        if (id) {
          const current = state.currentProducts.find((item) => Number(item.id) === Number(id));
          const source = demo.products.find((item) => Number(item.id) === Number(id));
          if (current) Object.assign(current, payload);
          if (source) Object.assign(source, payload);
        } else {
          demo.products.unshift({ ...payload, id: Date.now(), version: 1, sales: 0 });
        }
      } else {
        const response = await api(id ? "/admin/update" : "/admin/add", {
          method: id ? "PUT" : "POST",
          body: payload
        });
        ensureOk(response);
      }
      closeProductModal();
      showToast(id ? "商品已更新" : "商品已新增");
      await Promise.all([loadProducts(state.pages.products.current), refreshOverviewQuietly()]);
    } catch (error) {
      el["product-form-error"].textContent = error.message || "保存失败";
    } finally {
      el["save-product-button"].disabled = false;
    }
  }

  async function toggleProductStatus(productId, nextStatus) {
    try {
      if (state.preview) {
        const product = demo.products.find((item) => Number(item.id) === Number(productId));
        if (product) product.status = nextStatus;
      } else {
        const response = await api(`/admin/status/${productId}?status=${nextStatus}`, { method: "PUT" });
        ensureOk(response);
      }
      showToast(nextStatus === 1 ? "商品已上架" : "商品已下架");
      await loadProducts(state.pages.products.current);
    } catch (error) {
      showToast(error.message || "状态更新失败", true);
    }
  }

  function handleProductFilter(event) {
    const button = event.target.closest("[data-product-status], [data-product-stock]");
    if (!button) return;
    $$(".filter-chip", el["product-status-filters"]).forEach((item) => item.classList.toggle("active", item === button));
    if (button.dataset.productStatus !== undefined) {
      state.productStatus = button.dataset.productStatus;
      state.productStockThreshold = "";
    }
    if (button.dataset.productStock !== undefined) {
      state.productStatus = "";
      state.productStockThreshold = button.dataset.productStock;
    }
    loadProducts(1);
  }

  async function deleteProductItem(productId) {
    if (!window.confirm("确认删除该商品？历史订单中的商品快照不会受到影响。")) return;
    try {
      if (state.preview) {
        demo.products = demo.products.filter((item) => Number(item.id) !== Number(productId));
      } else {
        const response = await api(`/admin/${productId}`, { method: "DELETE" });
        ensureOk(response);
      }
      showToast("商品已删除");
      await Promise.all([loadProducts(state.pages.products.current), refreshOverviewQuietly()]);
    } catch (error) {
      showToast(error.message || "商品删除失败", true);
    }
  }

  async function loadUsers(page = 1, keyword = state.userKeyword) {
    state.usersLoaded = true;
    state.userKeyword = keyword;
    showLoading(el["users-table"], "正在读取用户…");
    try {
      let pageData;
      if (state.preview) {
        const filtered = demo.users.filter((item) => !keyword || item.username.toLowerCase().includes(keyword.toLowerCase()));
        pageData = paginate(filtered, page, state.pages.users.size);
      } else {
        const query = new URLSearchParams({ pageNum: String(page), pageSize: String(state.pages.users.size), username: keyword });
        const response = await api(`/admin/user/page?${query}`);
        ensureOk(response);
        pageData = response.data;
      }
      state.currentUsers = pageData.records || [];
      state.pages.users = {
        current: pageData.current || page,
        size: pageData.size || state.pages.users.size,
        pages: pageData.pages || 0,
        total: pageData.total || 0
      };
      el["users-table"].innerHTML = state.currentUsers.length
        ? userTable(state.currentUsers)
        : emptyState("没有找到用户", "尝试更换搜索词");
      renderPagination("users", el["users-pagination"]);
    } catch (error) {
      state.usersLoaded = false;
      el["users-table"].innerHTML = emptyState("用户加载失败", error.message);
      showToast(error.message || "用户加载失败", true);
    }
  }

  function userTable(users) {
    return `<table class="data-table admin-table">
      <thead><tr><th>用户</th><th>角色</th><th>状态</th><th>注册时间</th><th>操作</th></tr></thead>
      <tbody>${users.map((user) => `<tr>
        <td><div class="user-cell"><span class="avatar small-avatar">${esc((user.nickname || user.username || "用").slice(0, 1))}</span><div><strong>${esc(user.nickname || user.username)}</strong><small>${esc(user.username)} · ID ${esc(user.id)}</small></div></div></td>
        <td><span class="role-badge ${Number(user.role) === 1 ? "admin" : ""}">${Number(user.role) === 1 ? "管理员" : "普通用户"}</span></td>
        <td><span class="status-badge ${Number(user.status) === 1 ? "status-3" : "status-4"}">${Number(user.status) === 1 ? "正常" : "已冻结"}</span></td>
        <td>${formatDateTime(user.createTime)}</td>
        <td class="action-cell"><button class="table-action" data-user-addresses="${user.id}">查看地址</button>${Number(user.role) === 1 ? '<span class="muted-cell">管理员账户受保护</span>' : `<button class="table-action ${Number(user.status) === 1 ? "danger-text" : "primary-text"}" data-user-toggle="${user.id}" data-next-status="${Number(user.status) === 1 ? 0 : 1}">${Number(user.status) === 1 ? "冻结" : "解冻"}</button>`}</td>
      </tr>`).join("")}</tbody>
    </table>`;
  }

  async function toggleUserStatus(userId, nextStatus) {
    if (!window.confirm(nextStatus === 1 ? "确认解冻该用户？" : "确认冻结该用户？")) return;
    try {
      if (state.preview) {
        const user = demo.users.find((item) => Number(item.id) === Number(userId));
        if (user) user.status = nextStatus;
      } else {
        const response = await api(`/admin/user/status/${userId}?status=${nextStatus}`, { method: "PUT" });
        ensureOk(response);
      }
      showToast(nextStatus === 1 ? "用户已解冻" : "用户已冻结");
      await loadUsers(state.pages.users.current);
    } catch (error) {
      showToast(error.message || "用户状态更新失败", true);
    }
  }

  async function loadAddresses(page = 1, keyword = state.addressKeyword, userId = state.addressUserId) {
    state.addressesLoaded = true;
    state.addressKeyword = keyword;
    state.addressUserId = userId;
    showLoading(el["addresses-table"], "正在读取地址…");
    try {
      let pageData;
      if (state.preview) {
        const filtered = demo.addresses.filter((item) => {
          const matchesUser = !userId || String(item.userId) === String(userId);
          const search = String(keyword || "").toLowerCase();
          const matchesKeyword = !search || [item.name, item.phone, item.address, item.username]
            .some((value) => String(value || "").toLowerCase().includes(search));
          return matchesUser && matchesKeyword;
        });
        pageData = paginate(filtered, page, state.pages.addresses.size);
      } else {
        const query = new URLSearchParams({
          pageNum: String(page),
          pageSize: String(state.pages.addresses.size)
        });
        if (keyword) query.set("keyword", keyword);
        if (userId) query.set("userId", String(userId));
        const response = await api(`/admin/address/page?${query}`);
        ensureOk(response);
        pageData = response.data;
      }
      state.currentAddresses = pageData.records || [];
      state.pages.addresses = {
        current: pageData.current || page,
        size: pageData.size || state.pages.addresses.size,
        pages: pageData.pages || 0,
        total: pageData.total || 0
      };
      el["addresses-table"].innerHTML = state.currentAddresses.length
        ? addressTable(state.currentAddresses)
        : emptyState("没有找到地址", userId ? `用户 ID ${userId} 暂无地址记录` : "尝试更换搜索条件");
      renderPagination("addresses", el["addresses-pagination"]);
    } catch (error) {
      state.addressesLoaded = false;
      el["addresses-table"].innerHTML = emptyState("地址加载失败", error.message);
      showToast(error.message || "地址加载失败", true);
    }
  }

  function addressTable(addresses) {
    return `<table class="data-table admin-table address-table">
      <thead><tr><th>所属用户</th><th>收件人</th><th>手机号</th><th>收货地址</th><th>默认</th><th>创建时间</th><th>操作</th></tr></thead>
      <tbody>${addresses.map((address) => `<tr>
        <td><div class="user-cell"><span class="avatar small-avatar">${esc((address.nickname || address.username || "用").slice(0, 1))}</span><div><strong>${esc(address.nickname || address.username || "未命名用户")}</strong><small>${esc(address.username || "")} · ID ${esc(address.userId)}</small></div></div></td>
        <td>${esc(address.name)}</td>
        <td>${maskPhone(address.phone)}</td>
        <td><span class="address-preview" title="${escAttr(address.address)}">${esc(address.address)}</span></td>
        <td>${Number(address.isDefault) === 1 ? '<span class="status-badge status-3">默认</span>' : '<span class="muted-cell">否</span>'}</td>
        <td>${formatDateTime(address.createTime)}</td>
        <td class="action-cell"><button class="table-action primary-text" data-address-detail="${address.id}">详情</button></td>
      </tr>`).join("")}</tbody>
    </table>`;
  }

  async function openAddressDetail(addressId) {
    let address;
    try {
      if (state.preview) {
        address = demo.addresses.find((item) => Number(item.id) === Number(addressId));
      } else {
        const response = await api(`/admin/address/${addressId}`);
        ensureOk(response);
        address = response.data;
      }
      if (!address) throw new Error("地址不存在");
      el["detail-title"].textContent = "客户地址详情";
      el["order-detail-content"].innerHTML = addressDetailHtml(address);
      el["order-detail-drawer"].classList.add("open");
      el["detail-backdrop"].classList.add("open");
    } catch (error) {
      showToast(error.message || "地址详情加载失败", true);
    }
  }

  function addressDetailHtml(address) {
    return `<div class="detail-status-row"><span class="status-badge ${Number(address.isDefault) === 1 ? "status-3" : "status-4"}">${Number(address.isDefault) === 1 ? "默认地址" : "普通地址"}</span><strong>${esc(address.name)}</strong></div>
      <div class="detail-section"><h3>收货信息</h3><dl class="detail-list">
        <div><dt>所属用户</dt><dd>${esc(address.nickname || address.username || "—")}</dd></div>
        <div><dt>用户 ID</dt><dd>${esc(address.userId)}</dd></div>
        <div><dt>收件人</dt><dd>${esc(address.name)}</dd></div>
        <div><dt>手机号</dt><dd>${esc(address.phone)}</dd></div>
        <div><dt>详细地址</dt><dd>${esc(address.address)}</dd></div>
        <div><dt>创建时间</dt><dd>${formatDateTime(address.createTime)}</dd></div>
      </dl></div>
      <div class="readonly-note"><strong>只读管理</strong><p>客户地址属于个人资料，管理员工作台仅用于订单履约查询，不在这里修改或删除。</p></div>`;
  }

  function handleAddressSearch(event) {
    event.preventDefault();
    state.addressUserId = el["address-user-id-input"].value.trim();
    loadAddresses(1, el["address-search-input"].value.trim(), state.addressUserId);
  }

  function handleAddressUserFilter(event) {
    event.preventDefault();
    state.addressUserId = el["address-user-id-input"].value.trim();
    state.addressKeyword = el["address-search-input"].value.trim();
    loadAddresses(1, state.addressKeyword, state.addressUserId);
  }

  function maskPhone(phone) {
    const value = String(phone || "");
    return value.length === 11 ? `${value.slice(0, 3)}****${value.slice(-4)}` : esc(value || "—");
  }

  function renderPagination(target, container) {
    const page = state.pages[target];
    container.innerHTML = `<span>共 ${number(page.total)} 条</span><div>
      <button data-page-target="${target}" data-page="${page.current - 1}" ${page.current <= 1 ? "disabled" : ""}>上一页</button>
      <strong>${page.current || 1} / ${Math.max(page.pages || 1, 1)}</strong>
      <button data-page-target="${target}" data-page="${page.current + 1}" ${page.current >= page.pages ? "disabled" : ""}>下一页</button>
    </div>`;
  }

  function loadPage(target, page) {
    if (page < 1) return;
    if (target === "orders") loadOrders(page);
    if (target === "products") loadProducts(page);
    if (target === "users") loadUsers(page);
    if (target === "addresses") loadAddresses(page);
  }

  function handleProductSearch(event) {
    event.preventDefault();
    loadProducts(1, el["product-search-input"].value.trim());
  }

  function handleUserSearch(event) {
    event.preventDefault();
    loadUsers(1, el["user-search-input"].value.trim());
  }

  async function refreshOverviewQuietly() {
    if (state.preview) {
      applyOverviewMetrics(demo.overview);
      return;
    }
    try {
      const response = await api("/admin/ai/overview");
      if (response.code === "200") applyOverviewMetrics(response.data);
    } catch (error) {
      // The active page can still be used if the quiet overview refresh fails.
    }
  }

  function askFromAnywhere(prompt) {
    switchView("ai");
    if (!el["messages"].children.length) addWelcomeBlock();
    window.setTimeout(() => askAssistant(prompt), 30);
  }

  function startNewAiConversation() {
    switchView("ai");
    el["messages"].innerHTML = "";
    addWelcomeBlock();
    el["assistant-input"].focus();
  }

  async function askAssistant(question) {
    const text = String(question || "").trim();
    if (!text || state.busy) {
      if (state.busy) showToast("上一条分析仍在处理中");
      return;
    }
    state.busy = true;
    setComposerBusy(true);
    appendMessage("user", text);
    const typing = appendTyping();
    scrollMessages();
    try {
      const result = state.preview ? await previewAssistant(text) : await requestAssistant(text);
      removeNode(typing);
      const node = appendAssistantMessage(result);
      scrollToMessage(node);
    } catch (error) {
      removeNode(typing);
      const node = appendFailure(error.message || "AI 请求失败");
      scrollToMessage(node);
      showToast(error.message || "AI 请求失败", true);
    } finally {
      state.busy = false;
      setComposerBusy(false);
    }
  }

  async function requestAssistant(question) {
    const response = await api("/admin/ai/assistant", { method: "POST", body: { message: question } });
    if (response.code === "401" || response.code === "403") {
      logout();
      throw new Error("登录状态已失效");
    }
    ensureOk(response);
    return response.data;
  }

  function submitQuestion(event) {
    event.preventDefault();
    const value = el["assistant-input"].value.trim();
    if (!value) return;
    el["assistant-input"].value = "";
    resizeInput();
    askAssistant(value);
  }

  function handleMessageClick(event) {
    const prompt = event.target.closest("[data-prompt]");
    if (prompt) askAssistant(prompt.dataset.prompt);
  }

  function appendMessage(role, text, evidence = []) {
    const article = document.createElement("article");
    article.className = `message ${role}`;
    if (role === "assistant") {
      const avatar = document.createElement("div");
      avatar.className = "assistant-avatar";
      avatar.textContent = "✦";
      article.appendChild(avatar);
    }
    const body = document.createElement("div");
    body.className = "message-body";
    body.innerHTML = `<div class="message-meta"><strong>${role === "user" ? "你" : "运营助手"}</strong><span>${currentTime()}</span></div>`;
    const content = document.createElement("div");
    content.className = "message-text";
    content.textContent = text;
    body.appendChild(content);
    if (role === "assistant" && evidence.length) body.appendChild(renderEvidence(evidence));
    article.appendChild(body);
    if (role === "user") {
      const avatar = document.createElement("div");
      avatar.className = "user-avatar";
      avatar.textContent = userInitial();
      article.appendChild(avatar);
    }
    el["messages"].appendChild(article);
    return article;
  }

  function appendAssistantMessage(result) {
    const node = appendMessage("assistant", result.answer || "已完成分析。", result.evidence || []);
    const mode = result.mode === "openai" ? "OpenAI" : result.mode === "preview" ? "演示" : "本地摘要";
    $(".message-meta span", node).textContent = `${mode} · ${currentTime()}`;
    return node;
  }

  function appendFailure(message) {
    const node = appendMessage("assistant", `暂时无法完成分析。\n\n${message}`);
    $(".message-text", node).classList.add("error-message-text");
    return node;
  }

  function appendTyping() {
    const fragment = el["typing-template"].content.cloneNode(true);
    el["messages"].appendChild(fragment);
    return el["messages"].lastElementChild;
  }

  function addWelcomeBlock() {
    const block = document.createElement("div");
    block.className = "welcome-block";
    block.innerHTML = "<h3>想查询什么？</h3><p>可查询经营概览、订单、库存和销售趋势。</p>";
    el["messages"].appendChild(block);
  }

  function renderEvidence(evidenceList) {
    const wrapper = document.createElement("div");
    wrapper.className = "response-evidence";
    evidenceList.forEach((evidence) => {
      const card = document.createElement("section");
      card.className = "evidence-card";
      card.innerHTML = `<div class="evidence-head"><span>↗</span><strong>${evidenceLabel(evidence.name)}</strong><em>只读查询</em></div><div class="evidence-body"></div>`;
      const body = $(".evidence-body", card);
      renderEvidenceBody(body, evidence);
      const raw = document.createElement("details");
      raw.className = "raw-evidence";
      raw.innerHTML = "<summary>查看查询依据</summary><pre></pre>";
      $("pre", raw).textContent = JSON.stringify(evidence, null, 2);
      body.appendChild(raw);
      wrapper.appendChild(card);
    });
    return wrapper;
  }

  function renderEvidenceBody(container, evidence) {
    const result = evidence.result || {};
    if (evidence.name === "get_business_overview") {
      container.innerHTML = `<div class="mini-stat-grid">${[
        ["今日成交", money(result.todayTotalAmount)],
        ["今日订单", `${number(result.todayOrderCount)} 笔`],
        ["待发货", `${number(result.pendingShipmentCount)} 笔`],
        ["待付款", `${number(result.pendingPaymentCount)} 笔`],
        ["在售商品", `${number(result.activeGoodsCount)} 件`],
        ["低库存", `${number(result.lowStockGoodsCount)} 件`]
      ].map(([label, value]) => `<div class="mini-stat"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>`;
      return;
    }
    if (evidence.name === "query_orders") {
      const orders = result.orders || [];
      container.innerHTML = orders.length ? orderTable(orders, true) : '<p class="empty-evidence">没有找到符合条件的订单。</p>';
      return;
    }
    if (evidence.name === "query_low_stock_goods") {
      const goods = result.goods || [];
      container.innerHTML = goods.length ? `<div class="stock-list">${goods.map((item) => `<div class="stock-row"><div><strong>${esc(item.goodsName)}</strong><small>销量 ${number(item.sales)}</small></div><span class="stock-number">库存 ${number(item.stock)}</span></div>`).join("")}</div>` : '<p class="empty-evidence">当前没有低库存商品。</p>';
      return;
    }
    if (evidence.name === "get_sales_trend") {
      const trend = result.trend || [];
      const max = Math.max(...trend.map((item) => Number(item.totalAmount) || 0), 1);
      container.innerHTML = trend.length ? `<div class="trend-chart">${trend.map((item) => `<div class="trend-bar-wrap"><span class="trend-value">${compactMoney(item.totalAmount)}</span><span class="trend-bar" style="height:${Math.max(6, Math.round(Number(item.totalAmount) / max * 70))}px"></span><span class="trend-date">${shortDate(item.saleDate)}</span></div>`).join("")}</div>` : '<p class="empty-evidence">所选周期没有成交数据。</p>';
    }
  }

  async function previewAssistant(question) {
    await delay(650);
    const text = question.toLowerCase();
    const evidence = [];
    const answers = [];
    if (text.includes("概览") || text.includes("经营") || (!text.includes("库存") && !text.includes("趋势") && !text.includes("订单"))) {
      const overview = { ...demo.overview.overview };
      evidence.push({ name: "get_business_overview", arguments: {}, result: overview });
      answers.push(`今日成交 ${overview.todayOrderCount} 笔，金额 ${money(overview.todayTotalAmount)}；当前待发货 ${overview.pendingShipmentCount} 笔，低库存商品 ${overview.lowStockGoodsCount} 件。`);
    }
    if (text.includes("库存")) {
      evidence.push({ name: "query_low_stock_goods", arguments: { threshold: 10, limit: 10 }, result: demo.overview.lowStock });
      answers.push("库存预警：降噪蓝牙耳机 Pro 仅剩 3 件，便携咖啡杯剩 5 件，建议优先补货。");
    }
    if (text.includes("订单") || text.includes("待发货")) {
      const orders = demo.orders.filter((item) => item.status === 1).slice(0, 5);
      evidence.push({ name: "query_orders", arguments: { status: 1, limit: 10 }, result: { total: 12, status: 1, statusName: "待发货", orders } });
      answers.push("当前共有 12 笔待发货订单，建议优先处理今天上午创建的订单。");
    }
    if (text.includes("趋势") || text.includes("最近") || text.includes("7天")) {
      evidence.push({ name: "get_sales_trend", arguments: { days: 7 }, result: { days: 7, trend: demo.trend } });
      answers.push("最近 7 天成交整体平稳，周末出现明显增长。");
    }
    return { answer: answers.join("\n\n") || "已完成演示分析。", mode: "preview", evidence, configured: false };
  }

  function setMode(data) {
    const mode = data.mode || "unknown";
    state.mode = mode;
    const live = mode === "openai";
    const local = mode === "local";
    const preview = mode === "preview";
    const offline = mode === "offline";
    el["mode-label"].textContent = live ? "OpenAI 实时分析" : local ? "本地数据模式" : preview ? "演示数据预览" : "服务未连接";
    el["mode-chip"].classList.toggle("warning", !live);
    el["mode-chip"].classList.toggle("offline", offline);
    el["sidebar-status-light"].classList.toggle("warning", !live && !offline);
    el["sidebar-status-light"].classList.toggle("offline", offline);
    el["sidebar-status-title"].textContent = live ? "AI 引擎已连接" : local ? "本地摘要可用" : preview ? "演示模式" : "服务未连接";
    el["sidebar-status-copy"].textContent = live ? "OpenAI Responses API" : local ? "未配置 API Key" : preview ? "未读取真实数据" : "请启动后端";
    el["engine-title"].textContent = live ? "OpenAI 已启用" : local ? "本地规则模式" : preview ? "界面演示模式" : "连接失败";
    el["engine-description"].textContent = data.description || "当前使用本地业务摘要。";
    el["engine-meter-bar"].style.width = live ? "100%" : local ? "68%" : preview ? "46%" : "18%";
    el["chat-subtitle"].textContent = preview ? "正在展示前端演示数据" : "只读访问订单与商品数据";
  }

  function setCheckingMode() {
    el["mode-label"].textContent = "检查连接";
    el["engine-title"].textContent = "正在检测";
    el["engine-meter-bar"].style.width = "28%";
  }

  async function api(path, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 65000);
    const headers = { Accept: "application/json" };
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (options.auth !== false && state.token) headers.Authorization = `Bearer ${state.token}`;
    try {
      const response = await fetch(path, {
        method: options.method || "GET",
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal
      });
      if (!response.ok) throw new Error(`服务返回 HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      if (error.name === "AbortError") throw new Error("请求超时，请稍后重试");
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  function ensureOk(response) {
    if (!response || response.code !== "200") throw new Error(response?.msg || "操作失败");
  }

  function renderUser() {
    const name = state.user?.nickname || state.user?.username || "管理员";
    el["user-name"].textContent = name;
    el["user-avatar"].textContent = name.slice(0, 1);
    el["profile-mode"].textContent = state.preview ? "演示数据账号" : "管理员账户";
  }

  function showLoading(container, text) {
    container.innerHTML = `<div class="loading-row"><span class="loading-spinner"></span>${esc(text)}</div>`;
  }

  function emptyState(title, copy) {
    return `<div class="empty-state"><span>✦</span><strong>${esc(title)}</strong><p>${esc(copy || "")}</p></div>`;
  }

  function paginate(items, page, size) {
    const total = items.length;
    const pages = Math.ceil(total / size);
    const current = Math.min(Math.max(page, 1), Math.max(pages, 1));
    return { records: items.slice((current - 1) * size, current * size), total, pages, current, size };
  }

  function setLoginLoading(loading) {
    el["login-button"].disabled = loading;
    $("span", el["login-button"]).textContent = loading ? "正在验证…" : "进入工作台";
  }

  function togglePassword() {
    const showing = el.password.type === "text";
    el.password.type = showing ? "password" : "text";
    el["reveal-password"].textContent = showing ? "显示" : "隐藏";
  }

  function setComposerBusy(busy) {
    el["send-button"].disabled = busy;
    el["assistant-input"].disabled = busy;
    el["assistant-input"].placeholder = busy ? "正在读取业务数据…" : "询问订单、库存或销售趋势…";
  }

  function resizeInput() {
    el["assistant-input"].style.height = "auto";
    el["assistant-input"].style.height = `${Math.min(el["assistant-input"].scrollHeight, 115)}px`;
  }

  function handleComposerKeydown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      el["assistant-form"].requestSubmit();
    }
  }

  function closeDetailDrawer() {
    el["order-detail-drawer"].classList.remove("open");
    el["detail-backdrop"].classList.remove("open");
  }

  function openSidebar() {
    el.sidebar.classList.add("open");
    el["sidebar-backdrop"].classList.add("open");
  }

  function closeSidebar() {
    el.sidebar.classList.remove("open");
    el["sidebar-backdrop"].classList.remove("open");
  }

  function logout() {
    clearSession();
    el["dashboard-view"].classList.add("is-hidden");
    el["login-view"].classList.remove("is-hidden");
    el.password.value = "";
    el["login-error"].textContent = "";
  }

  function clearSession() {
    localStorage.removeItem(STORAGE.token);
    localStorage.removeItem(STORAGE.user);
    state.token = "";
    state.user = null;
    state.preview = false;
  }

  function showToast(message, error = false) {
    const node = document.createElement("div");
    node.className = `toast${error ? " error" : ""}`;
    node.textContent = message;
    el["toast-stack"].appendChild(node);
    setTimeout(() => node.remove(), 3600);
  }

  function scrollMessages() {
    requestAnimationFrame(() => { el.messages.scrollTop = el.messages.scrollHeight; });
  }

  function scrollToMessage(node) {
    requestAnimationFrame(() => {
      if (node) el.messages.scrollTop = Math.max(0, node.offsetTop - el.messages.offsetTop - 10);
    });
  }

  function removeNode(node) {
    if (node?.parentNode) node.parentNode.removeChild(node);
  }

  function updateDate() {
    const now = new Date();
    el["today-weekday"].textContent = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"][now.getDay()];
    el["today-date"].textContent = `${String(now.getMonth() + 1).padStart(2, "0")}月${String(now.getDate()).padStart(2, "0")}日`;
  }

  function userInitial() {
    return (state.user?.nickname || state.user?.username || "管").slice(0, 1);
  }

  function readJson(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
  }

  function statusName(status) {
    return ["待付款", "待发货", "已发货", "已完成", "已取消"][Number(status)] || "未知";
  }

  function evidenceLabel(name) {
    return { get_business_overview: "经营概览", query_orders: "订单查询", query_low_stock_goods: "库存预警", get_sales_trend: "销售趋势" }[name] || "业务查询";
  }

  function formatDateTime(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value).replace("T", " ").slice(0, 16);
    return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date);
  }

  function currentTime() {
    return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(new Date());
  }

  function money(value) {
    const amount = Number(value);
    return Number.isFinite(amount) ? `¥${amount.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : "—";
  }

  function compactMoney(value) {
    const amount = Number(value);
    return amount >= 10000 ? `${(amount / 10000).toFixed(1)}w` : String(Math.round(amount || 0));
  }

  function number(value) {
    const num = Number(value);
    return Number.isFinite(num) ? num.toLocaleString("zh-CN") : "—";
  }

  function shortDate(value) {
    const parts = String(value || "").split("-");
    return parts.length === 3 ? `${parts[1]}/${parts[2]}` : "—";
  }

  function shortOrderNo(value) {
    const text = String(value || "—");
    return text.length > 18 ? `${text.slice(0, 8)}…${text.slice(-6)}` : text;
  }

  function esc(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
  }

  function escAttr(value) {
    return esc(value).replaceAll("`", "&#096;");
  }

  function delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  function createDemoData() {
    const now = new Date();
    const iso = (offsetDays) => new Date(now.getTime() - offsetDays * 86400000).toISOString();
    const orders = [
      { id: 101, orderNo: "ORD202611160001", userId: 9001, status: 1, payAmount: 899, totalAmount: 899, addressName: "张先生", addressPhone: "138****8000", addressDetail: "上海市浦东新区演示地址 1 号", createTime: iso(0), payTime: iso(0), itemList: [{ goodsName: "降噪蓝牙耳机 Pro", goodsPrice: 399, buyNum: 2 }] },
      { id: 102, orderNo: "ORD202611160002", userId: 9002, status: 1, payAmount: 268, totalAmount: 268, addressName: "李女士", addressPhone: "139****9000", addressDetail: "杭州市西湖区演示地址 8 号", createTime: iso(0), payTime: iso(0), itemList: [{ goodsName: "便携咖啡杯", goodsPrice: 89, buyNum: 2 }] },
      { id: 103, orderNo: "ORD202611150003", userId: 9003, status: 0, payAmount: 129, totalAmount: 129, addressName: "王先生", addressPhone: "136****1000", addressDetail: "北京市朝阳区演示地址 6 号", createTime: iso(1), itemList: [{ goodsName: "桌面收纳套装", goodsPrice: 129, buyNum: 1 }] },
      { id: 104, orderNo: "ORD202611150004", userId: 9004, status: 2, payAmount: 559, totalAmount: 559, addressName: "赵女士", addressPhone: "137****2000", addressDetail: "深圳市南山区演示地址 3 号", createTime: iso(1), itemList: [{ goodsName: "真无线耳机 Air", goodsPrice: 559, buyNum: 1 }] },
      { id: 105, orderNo: "ORD202611140005", userId: 9005, status: 3, payAmount: 169, totalAmount: 169, addressName: "周先生", addressPhone: "135****3000", addressDetail: "成都市高新区演示地址 9 号", createTime: iso(2), itemList: [{ goodsName: "桌面收纳套装", goodsPrice: 169, buyNum: 1 }] },
      { id: 106, orderNo: "ORD202611140006", userId: 9006, status: 4, payAmount: 89, totalAmount: 89, addressName: "吴女士", addressPhone: "188****6000", addressDetail: "南京市鼓楼区演示地址 2 号", createTime: iso(2), itemList: [{ goodsName: "便携咖啡杯", goodsPrice: 89, buyNum: 1 }] }
    ];
    const products = [
      { id: 201, goodsName: "降噪蓝牙耳机 Pro", price: 399, stock: 3, sales: 86, status: 1, version: 1, pic_url: "" },
      { id: 202, goodsName: "便携咖啡杯 450ml", price: 89, stock: 5, sales: 124, status: 1, version: 1, pic_url: "" },
      { id: 203, goodsName: "桌面收纳套装", price: 129, stock: 8, sales: 47, status: 1, version: 1, pic_url: "" },
      { id: 204, goodsName: "真无线耳机 Air", price: 559, stock: 36, sales: 65, status: 1, version: 1, pic_url: "" },
      { id: 205, goodsName: "机械键盘 87 键", price: 459, stock: 42, sales: 38, status: 1, version: 1, pic_url: "" },
      { id: 206, goodsName: "便携充电宝 10000mAh", price: 199, stock: 64, sales: 92, status: 1, version: 1, pic_url: "" },
      { id: 207, goodsName: "护眼台灯 Mini", price: 239, stock: 21, sales: 29, status: 0, version: 1, pic_url: "" }
    ];
    const users = [
      { id: 1, username: "admin", nickname: "系统管理员", role: 1, status: 1, createTime: iso(120) },
      { id: 301, username: "zhangsan", nickname: "张三", role: 0, status: 1, createTime: iso(12) },
      { id: 302, username: "lisi", nickname: "李四", role: 0, status: 1, createTime: iso(8) },
      { id: 303, username: "wangwu", nickname: "王五", role: 0, status: 0, createTime: iso(4) }
    ];
    const addresses = [
      { id: 401, userId: 301, username: "zhangsan", nickname: "张三", name: "张先生", phone: "13800138000", address: "上海市浦东新区演示路 88 号 2 栋 1201", isDefault: 1, createTime: iso(12) },
      { id: 402, userId: 301, username: "zhangsan", nickname: "张三", name: "张先生", phone: "13800138000", address: "江苏省苏州市工业园区演示大道 16 号", isDefault: 0, createTime: iso(7) },
      { id: 403, userId: 302, username: "lisi", nickname: "李四", name: "李女士", phone: "13900139000", address: "杭州市西湖区演示街 9 号 3 幢 502", isDefault: 1, createTime: iso(8) },
      { id: 404, userId: 303, username: "wangwu", nickname: "王五", name: "王先生", phone: "13600136000", address: "北京市朝阳区演示路 6 号院 8 号楼", isDefault: 1, createTime: iso(4) }
    ];
    return {
      overview: {
        overview: { todayOrderCount: 12, todayTotalAmount: 18640, totalOrderCount: 318, totalAmount: 426580, pendingPaymentCount: 4, pendingShipmentCount: 12, shippedCount: 8, cancelledCount: 2, activeGoodsCount: 46, lowStockGoodsCount: 5 },
        lowStock: { threshold: 10, total: 3, goods: products.filter((item) => item.stock <= 10) }
      },
      orders,
      products,
      users,
      addresses,
      transform: products.slice(0, 3),
      trend: [4210, 3980, 5260, 6140, 4880, 8920, 7350].map((amount, index) => ({
        saleDate: new Date(now.getTime() - (6 - index) * 86400000).toISOString().slice(0, 10),
        orderCount: Math.round(amount / 800),
        totalAmount: amount
      }))
    };
  }
})();
