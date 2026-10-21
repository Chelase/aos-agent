"use strict";

const scenes = {
  chat: { title: "从一句话开始，连接你的模型与工具。", description: "使用你配置的 OpenAI 兼容模型进行对话，查看工具调用的过程与结果。也可以接入远端 MCP 来源，扩展 Agent 的工具。", image: "chat.png", icon: "i-chat", caption: "对话界面 · 内容为 UI 测试示例，不是实车实时数据", alt: "对话与工具调用界面，消息内容为明确标注的测试示例" },
  terminal: { title: "熟悉的系统 shell，就在手边。", description: "通过真实 PTY 会话输入命令、查看回显和滚动历史。在应用拥有的系统权限内完成诊断，不自带 root 或完整 Linux 发行版。", image: "terminal.png", icon: "i-terminal", caption: "终端界面 · AAOS 模拟器中的应用内 shell", alt: "真实 Android 终端界面，展示 shell 提示符与命令输入" },
  panel: { title: "看得见状态，也看得见缺失原因。", description: "集中查看设备、网络、电源与可读取的车辆属性。权限不足或厂商没有提供某项能力时，界面会明确说明。", image: "panel.png", icon: "i-car", caption: "系统面板 · 状态来自模拟器，不代表具体量产车型", alt: "系统面板，展示模拟器的网络、系统版本和电源信息" },
  voice: { title: "需要专注时，让交互变得更简单。", description: "检测到驾驶受限状态时显示语音遮罩。本地唤醒使用 Vosk；完整识别与播报仍取决于设备服务和麦克风权限。", image: "voice.png", icon: "i-microphone", caption: "驾驶受限界面预览 · 示例设备尚未授予麦克风权限", alt: "驾驶受限语音遮罩预览，明确显示麦克风权限缺失" },
};

const sceneDocs = {
  chat: ["了解模型配置", "配置模型服务"],
  terminal: ["了解终端能力", "已有功能"],
  panel: ["了解设备条件", "车型适配与已知限制"],
  voice: ["了解语音配置", "mcp-与语音"],
};

const tabs = [...document.querySelectorAll("[data-scene]")];
const panel = document.getElementById("scene-panel");
function selectScene(tab, moveFocus = false) {
  const key = tab.dataset.scene;
  const scene = scenes[key];
  if (!scene) return;
  for (const item of tabs) {
    const active = item === tab;
    item.setAttribute("aria-selected", String(active));
    item.tabIndex = active ? 0 : -1;
  }
  panel.setAttribute("aria-labelledby", tab.id);
  document.getElementById("scene-link-label").textContent = sceneDocs[key][0];
  document.getElementById("scene-link").href = `https://github.com/Chelase/aos-agent#${sceneDocs[key][1]}`;
  document.getElementById("scene-title").textContent = scene.title;
  document.getElementById("scene-description").textContent = scene.description;
  document.getElementById("scene-icon").className = `icon scene-icon ${scene.icon}`;
  const image = document.getElementById("scene-image");
  image.src = `./assets/screens/${scene.image}`;
  image.alt = scene.alt;
  document.getElementById("scene-caption").textContent = scene.caption;
  const zoom = document.getElementById("scene-zoom");
  zoom.dataset.zoom = image.getAttribute("src");
  zoom.dataset.caption = scene.caption;
  if (moveFocus) tab.focus({ preventScroll: true });
}
for (const [index, tab] of tabs.entries()) {
  tab.addEventListener("click", () => selectScene(tab));
  tab.addEventListener("keydown", event => {
    let next = index;
    if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
    else if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    else return;
    event.preventDefault();
    selectScene(tabs[next], true);
  });
}

const menu = document.querySelector(".menu-toggle");
const navigation = document.getElementById("main-nav");
function closeMenu() {
  menu.setAttribute("aria-expanded", "false");
  menu.setAttribute("aria-label", "打开导航菜单");
  navigation.classList.remove("is-open");
}
menu.addEventListener("click", () => {
  const expanded = menu.getAttribute("aria-expanded") === "true";
  menu.setAttribute("aria-expanded", String(!expanded));
  menu.setAttribute("aria-label", expanded ? "打开导航菜单" : "关闭导航菜单");
  navigation.classList.toggle("is-open", !expanded);
});
navigation.addEventListener("click", event => { if (event.target.closest("a")) closeMenu(); });
document.addEventListener("keydown", event => {
  if (event.key === "Escape" && menu.getAttribute("aria-expanded") === "true") {
    closeMenu();
    menu.focus();
  }
});
matchMedia("(min-width: 701px)").addEventListener("change", event => { if (event.matches) closeMenu(); });

const dialog = document.querySelector(".image-dialog");
let opener;
document.addEventListener("click", event => {
  const button = event.target.closest("[data-zoom]");
  if (!button) return;
  opener = button;
  const image = dialog.querySelector("img");
  image.src = button.dataset.zoom;
  image.alt = button.dataset.caption;
  dialog.querySelector("figcaption").textContent = button.dataset.caption;
  dialog.showModal();
});
dialog.querySelector(".dialog-close").addEventListener("click", () => dialog.close());
dialog.addEventListener("click", event => { if (event.target === dialog) dialog.close(); });
dialog.addEventListener("close", () => { opener?.focus({ preventScroll: true }); });
