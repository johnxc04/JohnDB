/*
 *@Type Client.java
 * @Desc
 * @Author neurone urmsone@163.com
 * @date 2024/6/13 13:15
 * @version
 */
package org.yy.client;

public interface Client {
    void set(String key, String value);

    String get(String key);

    void rm(String key);
}
