#!/system/bin/sh
# aos-llm — AOS-Agent 模型服务快速配置 CLI（本文件由应用生成安装，请勿手改）
#
# 写操作经显式组件广播落到应用私有存储（DataStore），API Key 永不回显明文；
# 读操作读取应用生成的 state.sh 快照。用法：aos-llm help

PKG="@PKG@"
BIN_DIR="@BIN_DIR@"
STATE="$BIN_DIR/../llm/state.sh"
RECV="$PKG/.receiver.LlmConfigReceiver"

# 本应用所属的 Android 用户（uid / 100000）。显式传 --user：
# 应用 uid 下 am 的 USER_CURRENT 解析可能被拒（INTERACT_ACROSS_USERS），实测 API 36 车机镜像会拦截。
AOS_USER=$(( $(id -u) / 100000 ))

load_state() {
  if [ -f "$STATE" ]; then . "$STATE"; fi
}

show_status() {
  load_state
  echo "当前模型服务："
  if [ -n "$AOS_LLM_URL" ]; then
    echo "  URL    $AOS_LLM_URL"
    echo "  Model  $AOS_LLM_MODEL"
    echo "  Key    ${AOS_LLM_KEY:-未设置}"
    if [ -n "$AOS_LLM_ACTIVE_PROFILE" ] && [ "$AOS_LLM_ACTIVE_PROFILE" != "-" ]; then
      echo "  档案   $AOS_LLM_ACTIVE_PROFILE"
    fi
  else
    echo "  未配置。先执行：aos-llm set --url <接口地址> --model <模型名> [--key <API Key>]"
  fi
  if [ -n "$AOS_LLM_PROFILES" ]; then
    echo "已存档案：$AOS_LLM_PROFILES"
  else
    echo "已存档案：无（aos-llm add <名字> --url ... --model ... 存档，aos-llm use 一键切换）"
  fi
}

usage() {
cat <<'EOF'
用法：aos-llm <命令> [参数]（车载拼音键盘打不出半角连字符时，可用别名 aosllm）

命令：
  status                  当前生效的模型服务配置（默认命令）
  list                    已保存的配置档案（Key 只显示掩码）
  set  --url URL --model M [--key K] [--name P] [--keep]
                          设置当前生效配置；--name 同时存为档案 P；
                          --keep 表示未给出的字段（URL/模型/Key）沿用当前配置
  add  NAME --url URL --model M [--key K]
                          保存/覆盖档案，不改变当前生效配置
  use  NAME               切换到档案 NAME（下一条对话即生效）
  rm   NAME               删除档案（不影响当前生效配置）
  sync                    重建本地状态缓存（自愈用）
  help                    本说明

示例：
  aos-llm set --url https://api.example.com/v1 --model gpt-4o-mini --key sk-xxx
  aos-llm set --url https://api.example.com/v1 --model gpt-4o-mini --name work
  aos-llm use work
EOF
}

# send [extra...]：显式组件广播（带本应用所属 user）；am 会等接收器处理完才返回。
# 接收器把执行结果（含错误原因）放在回执 data 里，提取出来如实回显。
send() {
  out="$(am broadcast --user "$AOS_USER" -n "$RECV" "$@" 2>&1)"
  case "$out" in
    *"Broadcast completed"*)
      msg="$(printf '%s\n' "$out" | sed -n 's/^.*data="\(.*\)".*$/\1/p')"
      [ -n "$msg" ] && echo "$msg"
      ;;
    *) echo "广播未送达（应用进程可能没在运行，先打开一次 App）：$out" ;;
  esac
}

fail() {
  echo "错误：$1"
  exit 2
}

cmd="${1:-status}"

case "$cmd" in
  status|-s)
    show_status
    ;;
  help|-h|--help)
    usage
    ;;
  list)
    load_state
    if [ -z "$AOS_LLM_PROFILES" ]; then
      echo "无档案。aos-llm add <名字> --url <URL> --model <模型名> 保存一个。"
      exit 0
    fi
    echo "已存档案（Key 为掩码）："
    for p in $AOS_LLM_PROFILES; do
      eval "purl=\$AOS_LLM_PROFILE_${p}_URL; pmodel=\$AOS_LLM_PROFILE_${p}_MODEL; pkey=\$AOS_LLM_PROFILE_${p}_KEY"
      echo "  $p"
      echo "    $pmodel @ $purl"
      echo "    Key=$pkey"
    done
    ;;
  set|add)
    url=""; model=""; key=""; name=""; keep=0
    if [ "$cmd" = "add" ]; then
      name="${2:-}"
      [ -n "$name" ] || fail "add 需要档案名：aos-llm add <名字> --url ... --model ..."
      shift 2
    else
      shift
    fi
    while [ $# -gt 0 ]; do
      case "$1" in
        --url) [ $# -ge 2 ] || fail "$1 缺参数值"; url="$2"; shift 2 ;;
        --model) [ $# -ge 2 ] || fail "$1 缺参数值"; model="$2"; shift 2 ;;
        --key) [ $# -ge 2 ] || fail "$1 缺参数值"; key="$2"; shift 2 ;;
        --name) [ $# -ge 2 ] || fail "$1 缺参数值"; name="$2"; shift 2 ;;
        --keep) keep=1; shift ;;
        *) fail "未知参数：$1（aos-llm help 看用法）" ;;
      esac
    done
    if [ "$cmd" = "add" ]; then
      [ -n "$name" ] || fail "add 需要档案名：aos-llm add <名字> --url ... --model ..."
    fi
    # --keep 时 URL/模型可省略（沿用当前配置）；不带 --keep 必须成对给出
    if [ "$keep" != "1" ]; then
      [ -n "$url" ] || fail "缺 --url"
      [ -n "$model" ] || fail "缺 --model"
    fi
    set --
    if [ "$cmd" = "set" ]; then
      set -- "$@" -a "$PKG.llm.SET_ACTIVE"
      [ -n "$name" ] && set -- "$@" --es name "$name"
      [ "$keep" = "1" ] && set -- "$@" --ez keep_key true
    else
      set -- "$@" -a "$PKG.llm.ADD_PROFILE" --es name "$name"
    fi
    [ -n "$url" ] && set -- "$@" --es url "$url"
    [ -n "$model" ] && set -- "$@" --es model "$model"
    [ -n "$key" ] && set -- "$@" --es key "$key"
    send "$@"
    echo
    show_status
    ;;
  use|rm)
    name="$2"
    [ -n "$name" ] || fail "$cmd 需要档案名"
    if [ "$cmd" = "use" ]; then
      send -a "$PKG.llm.USE_PROFILE" --es name "$name"
    else
      send -a "$PKG.llm.REMOVE_PROFILE" --es name "$name"
    fi
    echo
    show_status
    ;;
  sync)
    send -a "$PKG.llm.REFRESH"
    echo
    show_status
    ;;
  *)
    usage
    echo
    fail "未知命令：$cmd"
    ;;
esac
