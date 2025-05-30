# [Read from the standard input](https://kotlinlang.org/docs/basic-syntax.html#read-from-the-standard-input)

```kotlin
fun main() {
    // Prints a message to request input
    println("Enter any word: ")

    // Reads and stores the user input. For example: Happiness
    val yourWord = readln()

    // Prints a message with the input
    print("You entered the word: ")
    print(yourWord)
    // You entered the word: Happiness
}
```

```c#
public sealed class MainKt
{
    public static void main()
    {
        global::kotlin.io.ConsoleKt.println("Enter any word: ");
        global::System.String yourWord = global::kotlin.io.ConsoleKt.readln();
        global::kotlin.io.ConsoleKt.print("You entered the word: ");
        global::kotlin.io.ConsoleKt.print(yourWord);
    }
    public static void Main(global::System.String[] args)
    {
        main();
    }
}
```

# [Functions](https://kotlinlang.org/docs/basic-syntax.html#functions)

```kotlin
fun main() {
    println("sum(1, 2) = ${sum(1, 2)}")
}

fun sum(a: Int, b: Int): Int {
    return a + b
}
```

```c#
public sealed class MainKt
{
    public static void main()
    {
        global::kotlin.io.ConsoleKt.println("sum(1, 2) = " + global::MainKt.sum(1, 2));
    }
    public static global::System.Int32 sum(global::System.Int32 a, global::System.Int32 b)
    {
        return a + b;
    }
    public static void Main(global::System.String[] args)
    {
        main();
    }
}
```

```kotlin
fun printSum(a: Int, b: Int): Unit {
    println("sum of $a and $b is ${a + b}")
}
```

```c#
public sealed class MainKt
{
    public static void main()
    {
        global::MainKt.printSum(1, 2);
    }
    public static void printSum(global::System.Int32 a, global::System.Int32 b)
    {
        global::kotlin.io.ConsoleKt.println("sum of " + a + " and " + b + " is " + a + b);
    }
    public static void Main(global::System.String[] args)
    {
        global::MainKt.main();
    }
}
```