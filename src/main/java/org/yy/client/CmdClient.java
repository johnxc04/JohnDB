/*
 *@Type CmdClient.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 13:58
 * @version
 */
package org.yy.client;

import org.apache.commons.cli.*;

import java.util.Arrays;
import java.util.Scanner;

public class CmdClient{
    private Client client;

    public CmdClient(Client client) {
        this.client = client;
    }

    public void main(){
        Scanner scanner = new Scanner(System.in);
        String[] input = new String[1];
        while (true){
            input[0] =  scanner.nextLine();
            if (input[0].startsWith("set")){
                input[0] = "-s " + input[0].substring(3).trim(); // 从 "set" 后面开始截取，去除可能的前导空格
            }
            if (input[0].startsWith("get")){
                input[0] = "-g " + input[0].substring(3).trim(); // 从 "set" 后面开始截取，去除可能的前导空格
            }
            if (input[0].startsWith("remove")){
                input[0] = "-rm " + input[0].substring(3).trim(); // 从 "set" 后面开始截取，去除可能的前导空格
            }
            if (input[0].startsWith("help")){
                input[0] = "-h " + input[0].substring(3).trim(); // 从 "set" 后面开始截取，去除可能的前导空格
            }
            CMD(input);
        }
    }

    public void CMD(String[] input) {
        // 创建 Options 对象
        Options options = new Options();

        // 添加 -s 选项，它接受两个参数（key 和 value）
        Option setOption = Option.builder("s")
                .longOpt("set")
                .hasArg()
                .numberOfArgs(2)
                .argName("key value")
                .desc("设置键值")
                .build();
        options.addOption(setOption);

        // 添加 -g 选项，它接受一个参数（key）
        Option getOption = Option.builder("g")
                .longOpt("get")
                .hasArg()
                .argName("key")
                .desc("获取值")
                .build();
        options.addOption(getOption);

        // 添加 -rm 选项，它接受一个参数（key）
        Option rmOption = Option.builder("rm")
                .longOpt("remove")
                .hasArg()
                .argName("key")
                .desc("删除键值")
                .build();
        options.addOption(rmOption);

        // 添加 -h 或 --help 选项
        Option helpOption = Option.builder("h")
                .longOpt("help")
                .desc("打印帮助信息")
                .build();
        options.addOption(helpOption);

        // 创建 HelpFormatter
        HelpFormatter formatter = new HelpFormatter();

        // 创建 DefaultParser 对象
        CommandLineParser parser = new DefaultParser();

        // 解析命令行参数
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, input);
        } catch (ParseException e) {
            System.err.println("输入命令错误 原因：: " + e.getMessage());
            formatter.printHelp("Mesql", options);
            System.exit(1);
        }

        System.out.println(Arrays.toString(cmd.getOptions()));

        // 确保至少有一个选项被指定
        if (cmd.getOptions().length == 0) {
            PrintHelp(formatter, options);
            return;
        }

        // 进行条件判断
        if (cmd.hasOption("set")) {
            String[] setArgs = cmd.getOptionValue("set").split(" ");
            String key = setArgs[1];
            String value = setArgs[2];
            client.Set(key, value);
        } else if (cmd.hasOption("get")) {
            String[] getArgs = cmd.getOptionValue("get").split(" ");
            String key = getArgs[1];
            client.Get(key);
        } else if (cmd.hasOption("rm")) {
            String[] removeArgs = cmd.getOptionValue("rm").split(" ");
            String key = removeArgs[1];
            client.Remove(key);
        } else if (cmd.hasOption("h")) {
            PrintHelp(formatter, options);
        } else {
            PrintHelp(formatter, options);
        }
    }

    // 打印帮助信息
    private static void PrintHelp(HelpFormatter formatter, Options options) {
        formatter.printHelp("Mesql", options, true);
    }
}
