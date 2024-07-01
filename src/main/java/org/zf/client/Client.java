/*
 *@Type Client.java
 * @Desc
 * @Author neurone urmsone@163.com
 * @date 2024/6/13 13:15
 * @version
 */
package org.zf.client;

public interface Client {
    void Set(String key, String value);

    String Get(String key);

    void Remove(String key);

}
