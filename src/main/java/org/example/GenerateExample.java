package org.example;


import org.example.classes.BinaryTreeNode;
import org.example.classes.Cart;
import org.example.classes.Example;
import org.example.classes.Product;
import org.example.classes.Rectangle;
import org.example.classes.Shape;
import org.example.classes.Triangle;
import org.example.generator.Generator;

public class GenerateExample {
    public static void main(String[] args) {
        var gen = new Generator();
        try {
            Object generated = gen.generateValueOfType(Example.class);
            System.out.println(generated);

            Object cart = gen.generateValueOfType(Cart.class);
            System.out.println(cart);

            Object binaryTreeNode = gen.generateValueOfType(BinaryTreeNode.class);
            System.out.println(binaryTreeNode);

            Object product = gen.generateValueOfType(Product.class);
            System.out.println(product);

            Object rectangle = gen.generateValueOfType(Rectangle.class);
            System.out.println(rectangle);

            Shape shape = gen.generateValueOfType(Shape.class);
            System.out.println(shape);

            Triangle triangle = gen.generateValueOfType(Triangle.class);
            System.out.println(triangle);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}