package com.hrm.lumina.core

/**
 * 粒子对象池。
 * 预分配固定数量的 [Particle] 实例，通过 [acquire]/[release] 复用，避免频繁 GC。
 *
 * @param capacity 池容量，即最大同时存活的粒子数
 */
class ParticlePool(val capacity: Int) {

    /** 所有粒子实例（固定大小数组） */
    private val pool = Array(capacity) { Particle() }

    /** 标记每个槽位是否正在使用 */
    private val inUse = BooleanArray(capacity) { false }

    /** 当前活跃粒子数 */
    var activeCount: Int = 0
        private set

    /**
     * 从池中获取一个空闲粒子并标记为使用中。
     * 若池已满，则强制回收生命值剩余最少（最老）的粒子并复用，
     * 确保发射原点移动后新位置能持续发射粒子。
     */
    fun acquire(): Particle? {
        // 优先找空闲槽
        for (i in 0 until capacity) {
            if (!inUse[i]) {
                inUse[i] = true
                pool[i].reset()
                activeCount++
                return pool[i]
            }
        }
        // 池满：强制回收生命值剩余最少的粒子
        var minLife = Float.MAX_VALUE
        var minIdx = -1
        for (i in 0 until capacity) {
            if (pool[i].life < minLife) {
                minLife = pool[i].life
                minIdx = i
            }
        }
        if (minIdx < 0) return null
        pool[minIdx].reset()
        // activeCount 不变（回收一个再占用一个）
        return pool[minIdx]
    }

    /**
     * 将粒子归还到池中。
     * 注意：必须归还从本池 [acquire] 出来的粒子。
     */
    fun release(particle: Particle) {
        for (i in 0 until capacity) {
            if (pool[i] === particle) {
                inUse[i] = false
                activeCount--
                return
            }
        }
    }

    /**
     * 遍历所有活跃粒子，对生命值耗尽的粒子自动回收。
     * 在 [ParticleEngine.tick] 中调用。
     */
    fun tickAndRecycle(block: (Particle) -> Unit) {
        for (i in 0 until capacity) {
            if (inUse[i]) {
                val p = pool[i]
                block(p)
                if (!p.isAlive) {
                    inUse[i] = false
                    activeCount--
                }
            }
        }
    }

    /** 获取所有活跃粒子的只读快照（用于渲染） */
    fun activeParticles(): List<Particle> {
        val result = mutableListOf<Particle>()
        for (i in 0 until capacity) {
            if (inUse[i]) result.add(pool[i])
        }
        return result
    }

    /** 清空所有粒子 */
    fun clear() {
        inUse.fill(false)
        activeCount = 0
    }
}
