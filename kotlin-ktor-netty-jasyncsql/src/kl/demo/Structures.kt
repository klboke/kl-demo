package me.hltj.kaggregator.demo

interface HasId {
    val id: Int
}

data class Person(
        override val id: Int,
        val age: Int,
        val name: String
): HasId

