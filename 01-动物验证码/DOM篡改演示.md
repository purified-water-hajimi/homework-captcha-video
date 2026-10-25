# 演示：前端校验为什么不可靠（DOM 篡改实操）

> 目标：打开 `demo/login-demo.html`，用浏览器开发者工具绕过验证码。
> 目的：说明**前端校验只能防普通用户，防不住懂技术的人**，所以正式版必须放服务器。

---

## 准备

1. 双击打开 `demo/login-demo.html`
2. 按 **F12**（或右键 → 检查）
3. 切到 **Console（控制台）** 标签

---

## 手法一：直接偷看答案

演示版的答案存在一个叫 `answer` 的变量里。在 Console 输入：

```js
answer
```

回车，会直接打印出：

```js
{name: "猫", emoji: "🐱", aliases: Array(4)}
```

**结论**：答案就在浏览器内存里，谁都能看。

---

## 手法二：自动填答案，不用看图

接着输入：

```js
document.getElementById("captchaInput").value = answer.name
```

回车后，输入框里自动出现「猫」。

再点「进入学习空间」——**直接通过，你根本没看图**。

**结论**：验证码形同虚设。

---

## 手法三：直接改判断函数

```js
checkAnswer = () => true
```

然后再点登录，**任何输入都能过**。

**结论**：连函数本身都能被替换。

---

## 手法四：伪造成功提示

```js
document.getElementById("error").classList.add("hidden");
document.getElementById("notice").textContent = "验证通过，欢迎回来！";
document.getElementById("notice").classList.remove("hidden");
```

页面上出现一行绿色的「验证通过」，**但实际上什么都没验证**。

**结论**：DOM 上的任何文字都可以是假的，截图不能当证据。

---

## 手法五：往页面里塞东西

```js
const fake = document.createElement("p");
fake.textContent = "本页面已通过安全认证 ✅";
fake.style.color = "#0f7b3f";
document.querySelector("main").prepend(fake);
```

页面顶部多了一行“安全认证”——完全是编的。

---

## 为什么这些都没用

以上所有操作，**改的都只是浏览器里的 DOM**。它们有两个共同下场：

1. **刷新就没了**：DOM 是内存里的临时结构，刷新后按 HTML 文件重建；
2. **服务器不知道**：`login-demo.html` 是本地文件，根本没有服务器参与。

而正式版（`CaptchaServlet.java`）是这样工作的：

```
1. 服务器生成图片，答案写进 Session（浏览器只拿到图片）
2. 用户提交 animal=猫
3. 服务器拿 Session 里的答案比对
4. 不对就 400，对才走登录
```

**关键**：第 3 步发生在服务器上，浏览器里怎么改都碰不到那个 Session。

---

## 反过来说，正式版还能怎么防

| 攻击方式 | 正式版的防守 |
|---|---|
| 看答案变量 | 答案不在前端 |
| 改校验函数 | 校验在服务端 |
| 重放同一张图 | 校验一次就作废 |
| 慢慢穷举 | 3 分钟过期 |
| 批量脚本刷 | 每次登录都要求新图 |

---

## 一句话结论（可直接写进报告）

> 前端的任何校验都只是**用户体验优化**，不是安全措施。
> 真正的校验必须发生在服务端，因为浏览器里的数据用户想怎么改就怎么改。
