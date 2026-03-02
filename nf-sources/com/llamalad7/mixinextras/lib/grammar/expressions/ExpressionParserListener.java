// Generated from ExpressionParser.g4 by ANTLR 4.13.1
package com.llamalad7.mixinextras.lib.grammar.expressions;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ExpressionParser}.
 */
public interface ExpressionParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ExpressionParser#root}.
	 * @param ctx the parse tree
	 */
	void enterRoot(ExpressionParser.RootContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExpressionParser#root}.
	 * @param ctx the parse tree
	 */
	void exitRoot(ExpressionParser.RootContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MemberAssignmentStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterMemberAssignmentStatement(ExpressionParser.MemberAssignmentStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MemberAssignmentStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitMemberAssignmentStatement(ExpressionParser.MemberAssignmentStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayStoreStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterArrayStoreStatement(ExpressionParser.ArrayStoreStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayStoreStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitArrayStoreStatement(ExpressionParser.ArrayStoreStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IdentifierAssignmentStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierAssignmentStatement(ExpressionParser.IdentifierAssignmentStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IdentifierAssignmentStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierAssignmentStatement(ExpressionParser.IdentifierAssignmentStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ReturnStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterReturnStatement(ExpressionParser.ReturnStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ReturnStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitReturnStatement(ExpressionParser.ReturnStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ThrowStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterThrowStatement(ExpressionParser.ThrowStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ThrowStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitThrowStatement(ExpressionParser.ThrowStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterExpressionStatement(ExpressionParser.ExpressionStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionStatement}
	 * labeled alternative in {@link ExpressionParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitExpressionStatement(ExpressionParser.ExpressionStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BitwiseXorExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterBitwiseXorExpression(ExpressionParser.BitwiseXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BitwiseXorExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitBitwiseXorExpression(ExpressionParser.BitwiseXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ClassConstantExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterClassConstantExpression(ExpressionParser.ClassConstantExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ClassConstantExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitClassConstantExpression(ExpressionParser.ClassConstantExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code StaticMethodCallExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterStaticMethodCallExpression(ExpressionParser.StaticMethodCallExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code StaticMethodCallExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitStaticMethodCallExpression(ExpressionParser.StaticMethodCallExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BoolLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterBoolLitExpression(ExpressionParser.BoolLitExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BoolLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitBoolLitExpression(ExpressionParser.BoolLitExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UnaryExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterUnaryExpression(ExpressionParser.UnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UnaryExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitUnaryExpression(ExpressionParser.UnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FreeMethodReferenceExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterFreeMethodReferenceExpression(ExpressionParser.FreeMethodReferenceExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FreeMethodReferenceExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitFreeMethodReferenceExpression(ExpressionParser.FreeMethodReferenceExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ConstructorReferenceExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterConstructorReferenceExpression(ExpressionParser.ConstructorReferenceExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ConstructorReferenceExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitConstructorReferenceExpression(ExpressionParser.ConstructorReferenceExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code InstantiationExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterInstantiationExpression(ExpressionParser.InstantiationExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code InstantiationExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitInstantiationExpression(ExpressionParser.InstantiationExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IntLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterIntLitExpression(ExpressionParser.IntLitExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IntLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitIntLitExpression(ExpressionParser.IntLitExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ThisExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterThisExpression(ExpressionParser.ThisExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ThisExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitThisExpression(ExpressionParser.ThisExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DecimalLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterDecimalLitExpression(ExpressionParser.DecimalLitExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DecimalLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitDecimalLitExpression(ExpressionParser.DecimalLitExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MethodCallExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterMethodCallExpression(ExpressionParser.MethodCallExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MethodCallExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitMethodCallExpression(ExpressionParser.MethodCallExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code InstanceofExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterInstanceofExpression(ExpressionParser.InstanceofExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code InstanceofExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitInstanceofExpression(ExpressionParser.InstanceofExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WildcardExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterWildcardExpression(ExpressionParser.WildcardExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WildcardExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitWildcardExpression(ExpressionParser.WildcardExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterArrayLitExpression(ExpressionParser.ArrayLitExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitArrayLitExpression(ExpressionParser.ArrayLitExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code StringLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterStringLitExpression(ExpressionParser.StringLitExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code StringLitExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitStringLitExpression(ExpressionParser.StringLitExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code EqualityExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterEqualityExpression(ExpressionParser.EqualityExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code EqualityExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitEqualityExpression(ExpressionParser.EqualityExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MultiplicativeExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicativeExpression(ExpressionParser.MultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MultiplicativeExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicativeExpression(ExpressionParser.MultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BitwiseOrExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterBitwiseOrExpression(ExpressionParser.BitwiseOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BitwiseOrExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitBitwiseOrExpression(ExpressionParser.BitwiseOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ParenthesizedExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterParenthesizedExpression(ExpressionParser.ParenthesizedExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ParenthesizedExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitParenthesizedExpression(ExpressionParser.ParenthesizedExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AdditiveExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveExpression(ExpressionParser.AdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AdditiveExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveExpression(ExpressionParser.AdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MemberAccessExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterMemberAccessExpression(ExpressionParser.MemberAccessExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MemberAccessExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitMemberAccessExpression(ExpressionParser.MemberAccessExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BoundMethodReferenceExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterBoundMethodReferenceExpression(ExpressionParser.BoundMethodReferenceExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BoundMethodReferenceExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitBoundMethodReferenceExpression(ExpressionParser.BoundMethodReferenceExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ShiftExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterShiftExpression(ExpressionParser.ShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ShiftExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitShiftExpression(ExpressionParser.ShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CapturingExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterCapturingExpression(ExpressionParser.CapturingExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CapturingExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitCapturingExpression(ExpressionParser.CapturingExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NullExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterNullExpression(ExpressionParser.NullExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NullExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitNullExpression(ExpressionParser.NullExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IdentifierExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierExpression(ExpressionParser.IdentifierExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IdentifierExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierExpression(ExpressionParser.IdentifierExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BitwiseAndExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterBitwiseAndExpression(ExpressionParser.BitwiseAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BitwiseAndExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitBitwiseAndExpression(ExpressionParser.BitwiseAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ComparisonExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterComparisonExpression(ExpressionParser.ComparisonExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ComparisonExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitComparisonExpression(ExpressionParser.ComparisonExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SuperCallExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterSuperCallExpression(ExpressionParser.SuperCallExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SuperCallExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitSuperCallExpression(ExpressionParser.SuperCallExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CastExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterCastExpression(ExpressionParser.CastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CastExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitCastExpression(ExpressionParser.CastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NewArrayExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterNewArrayExpression(ExpressionParser.NewArrayExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NewArrayExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitNewArrayExpression(ExpressionParser.NewArrayExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayAccessExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterArrayAccessExpression(ExpressionParser.ArrayAccessExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayAccessExpression}
	 * labeled alternative in {@link ExpressionParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitArrayAccessExpression(ExpressionParser.ArrayAccessExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IdentifierName}
	 * labeled alternative in {@link ExpressionParser#name}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierName(ExpressionParser.IdentifierNameContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IdentifierName}
	 * labeled alternative in {@link ExpressionParser#name}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierName(ExpressionParser.IdentifierNameContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WildcardName}
	 * labeled alternative in {@link ExpressionParser#name}.
	 * @param ctx the parse tree
	 */
	void enterWildcardName(ExpressionParser.WildcardNameContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WildcardName}
	 * labeled alternative in {@link ExpressionParser#name}.
	 * @param ctx the parse tree
	 */
	void exitWildcardName(ExpressionParser.WildcardNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExpressionParser#nameWithDims}.
	 * @param ctx the parse tree
	 */
	void enterNameWithDims(ExpressionParser.NameWithDimsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExpressionParser#nameWithDims}.
	 * @param ctx the parse tree
	 */
	void exitNameWithDims(ExpressionParser.NameWithDimsContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExpressionParser#arguments}.
	 * @param ctx the parse tree
	 */
	void enterArguments(ExpressionParser.ArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExpressionParser#arguments}.
	 * @param ctx the parse tree
	 */
	void exitArguments(ExpressionParser.ArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExpressionParser#nonEmptyArguments}.
	 * @param ctx the parse tree
	 */
	void enterNonEmptyArguments(ExpressionParser.NonEmptyArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExpressionParser#nonEmptyArguments}.
	 * @param ctx the parse tree
	 */
	void exitNonEmptyArguments(ExpressionParser.NonEmptyArgumentsContext ctx);
}