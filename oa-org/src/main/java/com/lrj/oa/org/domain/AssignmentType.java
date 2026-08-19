package com.lrj.oa.org.domain;

/**
 * 任职类型 —— 一人多岗的表达方式。
 *
 * <ul>
 *   <li>{@code PRIMARY} 主岗：一人同一时刻至多一个（数据库偏唯一索引强制），人事口径与默认数据范围取它；</li>
 *   <li>{@code CONCURRENT} 兼岗：真实兼任，权限与数据范围会叠加；</li>
 *   <li>{@code DOTTED} 虚线：矩阵式管理下的归属，默认<b>不</b>叠加数据范围，只影响汇报与知会。</li>
 * </ul>
 */
public enum AssignmentType { PRIMARY, CONCURRENT, DOTTED }
