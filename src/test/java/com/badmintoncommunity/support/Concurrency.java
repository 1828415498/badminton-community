package com.badmintoncommunity.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * 并发用例的执行器：让 N 个线程<b>尽可能同时</b>撞上同一段业务代码。
 *
 * <h2>为什么要用闸门，而不是直接开 N 个线程</h2>
 * <p>如果只是 {@code for} 循环提交任务，先提交的线程早就跑完提交了，后提交的才开始 ——
 * 两者时间上根本不相交，等于<b>串行执行</b>，测出来永远是绿的，但什么也没验证到。
 * 两道 {@link CountDownLatch} 的作用是：所有线程先就位，再一起冲 —— 制造真实争抢。</p>
 *
 * <h2>为什么用「结果分布」而不是「第几个成功」</h2>
 * <p>并发下谁先拿到锁是不确定的，断言"1 号线程成功"必然不稳。所以任务的返回值统一是
 * <b>一个结果标签</b>（"OK" 或错误码），最后由调用方断言标签的<b>数量分布</b> ——
 * 这才是不依赖调度顺序的不变量。</p>
 */
public final class Concurrency {

    private Concurrency() {
    }

    /**
     * 让 {@code threads} 个线程同时执行 {@code task}，返回"结果标签 → 出现次数"的分布。
     *
     * <p>{@code task} 接收线程下标，应自行把异常翻译成标签返回（例如把
     * {@code BusinessException} 的 code 直接返回），不要往抛 —— 抛出去的异常会变成
     * 执行失败，而不是"某个参与者被拒绝了"。</p>
     *
     * @param threads 并发线程数（同时决定连接池压力）
     * @param task    每个线程要执行的动作，返回一个结果标签
     * @return 标签 → 次数的有序分布
     */
    public static Map<String, Integer> race(int threads, IntFunction<String> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch allReady = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    allReady.countDown();
                    startGate.await();
                    return task.apply(index);
                }));
            }

            // 等所有线程都到了起跑线再放闸；超时说明有线程没起来，直接失败更诚实
            if (!allReady.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发线程未能全部就位");
            }
            startGate.countDown();

            Map<String, Integer> outcome = new TreeMap<>();
            for (Future<String> future : futures) {
                outcome.merge(future.get(60, TimeUnit.SECONDS), 1, Integer::sum);
            }
            return outcome;
        } finally {
            pool.shutdownNow();
        }
    }
}
