package com.qshop.shop;

/**
 * 购买指令:交易完成后在服务器端执行的指令。
 */
public class ShopCommand {

    /** 指令内容(不带开头的斜杠),支持占位符,见 README */
    public String command = "";

    /** 是否以 4 级权限(相当于控制台权限)执行;false 则以 0 级权限执行 */
    public boolean op = false;

    /** 是否静默执行(抑制指令成功消息输出) */
    public boolean silent = true;

    public ShopCommand() {
    }

    public ShopCommand(String command, boolean op, boolean silent) {
        this.command = command;
        this.op = op;
        this.silent = silent;
    }
}
