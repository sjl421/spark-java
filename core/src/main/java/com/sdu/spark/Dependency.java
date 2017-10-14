package com.sdu.spark;

import com.sdu.spark.rdd.RDD;

import java.io.Serializable;

/**
 * @author hanhan.zhang
 * */
public abstract class Dependency<T> implements Serializable {

    public abstract RDD<T> rdd();

}
