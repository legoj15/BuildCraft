// Generated from ExpressionParser.g4 by ANTLR 4.13.1
package com.llamalad7.mixinextras.lib.grammar.expressions;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class ExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		NewLine=1, WS=2, StringLit=3, Wildcard=4, New=5, Instanceof=6, BoolLit=7, 
		NullLit=8, Return=9, Throw=10, This=11, Super=12, Class=13, Reserved=14, 
		Identifier=15, IntLit=16, DecLit=17, Plus=18, Minus=19, Mult=20, Div=21, 
		Mod=22, BitwiseNot=23, Dot=24, Comma=25, LeftParen=26, RightParen=27, 
		LeftBracket=28, RightBracket=29, LeftBrace=30, RightBrace=31, At=32, Shl=33, 
		Shr=34, Ushr=35, Lt=36, Le=37, Gt=38, Ge=39, Eq=40, Ne=41, BitwiseAnd=42, 
		BitwiseXor=43, BitwiseOr=44, Assign=45, MethodRef=46, Increment=47, Decrement=48;
	public static final int
		RULE_root = 0, RULE_statement = 1, RULE_expression = 2, RULE_name = 3, 
		RULE_nameWithDims = 4, RULE_arguments = 5, RULE_nonEmptyArguments = 6;
	private static String[] makeRuleNames() {
		return new String[] {
			"root", "statement", "expression", "name", "nameWithDims", "arguments", 
			"nonEmptyArguments"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, "'?'", "'new'", "'instanceof'", null, "'null'", 
			"'return'", "'throw'", "'this'", "'super'", "'class'", null, null, null, 
			null, "'+'", "'-'", "'*'", "'/'", "'%'", "'~'", "'.'", "','", "'('", 
			"')'", "'['", "']'", "'{'", "'}'", "'@'", "'<<'", "'>>'", "'>>>'", "'<'", 
			"'<='", "'>'", "'>='", "'=='", "'!='", "'&'", "'^'", "'|'", "'='", "'::'", 
			"'++'", "'--'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "NewLine", "WS", "StringLit", "Wildcard", "New", "Instanceof", 
			"BoolLit", "NullLit", "Return", "Throw", "This", "Super", "Class", "Reserved", 
			"Identifier", "IntLit", "DecLit", "Plus", "Minus", "Mult", "Div", "Mod", 
			"BitwiseNot", "Dot", "Comma", "LeftParen", "RightParen", "LeftBracket", 
			"RightBracket", "LeftBrace", "RightBrace", "At", "Shl", "Shr", "Ushr", 
			"Lt", "Le", "Gt", "Ge", "Eq", "Ne", "BitwiseAnd", "BitwiseXor", "BitwiseOr", 
			"Assign", "MethodRef", "Increment", "Decrement"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "ExpressionParser.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ExpressionParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RootContext extends ParserRuleContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public TerminalNode EOF() { return getToken(ExpressionParser.EOF, 0); }
		public RootContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_root; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterRoot(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitRoot(this);
		}
	}

	public final RootContext root() throws RecognitionException {
		RootContext _localctx = new RootContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_root);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(14);
			statement();
			setState(15);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
	 
		public StatementContext() { }
		public void copyFrom(StatementContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayStoreStatementContext extends StatementContext {
		public ExpressionContext arr;
		public ExpressionContext index;
		public ExpressionContext value;
		public TerminalNode LeftBracket() { return getToken(ExpressionParser.LeftBracket, 0); }
		public TerminalNode RightBracket() { return getToken(ExpressionParser.RightBracket, 0); }
		public TerminalNode Assign() { return getToken(ExpressionParser.Assign, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ArrayStoreStatementContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterArrayStoreStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitArrayStoreStatement(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionStatementContext extends StatementContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExpressionStatementContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterExpressionStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitExpressionStatement(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierAssignmentStatementContext extends StatementContext {
		public NameContext identifier;
		public ExpressionContext value;
		public TerminalNode Assign() { return getToken(ExpressionParser.Assign, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public IdentifierAssignmentStatementContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterIdentifierAssignmentStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitIdentifierAssignmentStatement(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ReturnStatementContext extends StatementContext {
		public ExpressionContext value;
		public TerminalNode Return() { return getToken(ExpressionParser.Return, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnStatementContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterReturnStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitReturnStatement(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ThrowStatementContext extends StatementContext {
		public ExpressionContext value;
		public TerminalNode Throw() { return getToken(ExpressionParser.Throw, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ThrowStatementContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterThrowStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitThrowStatement(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberAssignmentStatementContext extends StatementContext {
		public ExpressionContext receiver;
		public NameContext memberName;
		public ExpressionContext value;
		public TerminalNode Dot() { return getToken(ExpressionParser.Dot, 0); }
		public TerminalNode Assign() { return getToken(ExpressionParser.Assign, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public MemberAssignmentStatementContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterMemberAssignmentStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitMemberAssignmentStatement(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		try {
			setState(39);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
			case 1:
				_localctx = new MemberAssignmentStatementContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(17);
				((MemberAssignmentStatementContext)_localctx).receiver = expression(0);
				setState(18);
				match(Dot);
				setState(19);
				((MemberAssignmentStatementContext)_localctx).memberName = name();
				setState(20);
				match(Assign);
				setState(21);
				((MemberAssignmentStatementContext)_localctx).value = expression(0);
				}
				break;
			case 2:
				_localctx = new ArrayStoreStatementContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(23);
				((ArrayStoreStatementContext)_localctx).arr = expression(0);
				setState(24);
				match(LeftBracket);
				setState(25);
				((ArrayStoreStatementContext)_localctx).index = expression(0);
				setState(26);
				match(RightBracket);
				setState(27);
				match(Assign);
				setState(28);
				((ArrayStoreStatementContext)_localctx).value = expression(0);
				}
				break;
			case 3:
				_localctx = new IdentifierAssignmentStatementContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(30);
				((IdentifierAssignmentStatementContext)_localctx).identifier = name();
				setState(31);
				match(Assign);
				setState(32);
				((IdentifierAssignmentStatementContext)_localctx).value = expression(0);
				}
				break;
			case 4:
				_localctx = new ReturnStatementContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(34);
				match(Return);
				setState(35);
				((ReturnStatementContext)_localctx).value = expression(0);
				}
				break;
			case 5:
				_localctx = new ThrowStatementContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(36);
				match(Throw);
				setState(37);
				((ThrowStatementContext)_localctx).value = expression(0);
				}
				break;
			case 6:
				_localctx = new ExpressionStatementContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(38);
				expression(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BitwiseXorExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public ExpressionContext right;
		public TerminalNode BitwiseXor() { return getToken(ExpressionParser.BitwiseXor, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BitwiseXorExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterBitwiseXorExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitBitwiseXorExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ClassConstantExpressionContext extends ExpressionContext {
		public NameWithDimsContext type;
		public TerminalNode Dot() { return getToken(ExpressionParser.Dot, 0); }
		public TerminalNode Class() { return getToken(ExpressionParser.Class, 0); }
		public NameWithDimsContext nameWithDims() {
			return getRuleContext(NameWithDimsContext.class,0);
		}
		public ClassConstantExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterClassConstantExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitClassConstantExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StaticMethodCallExpressionContext extends ExpressionContext {
		public NameContext memberName;
		public ArgumentsContext args;
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public ArgumentsContext arguments() {
			return getRuleContext(ArgumentsContext.class,0);
		}
		public StaticMethodCallExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterStaticMethodCallExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitStaticMethodCallExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BoolLitExpressionContext extends ExpressionContext {
		public Token lit;
		public TerminalNode BoolLit() { return getToken(ExpressionParser.BoolLit, 0); }
		public BoolLitExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterBoolLitExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitBoolLitExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnaryExpressionContext extends ExpressionContext {
		public Token op;
		public ExpressionContext expr;
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode Minus() { return getToken(ExpressionParser.Minus, 0); }
		public TerminalNode BitwiseNot() { return getToken(ExpressionParser.BitwiseNot, 0); }
		public UnaryExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterUnaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitUnaryExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FreeMethodReferenceExpressionContext extends ExpressionContext {
		public NameContext memberName;
		public TerminalNode MethodRef() { return getToken(ExpressionParser.MethodRef, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public FreeMethodReferenceExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterFreeMethodReferenceExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitFreeMethodReferenceExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ConstructorReferenceExpressionContext extends ExpressionContext {
		public NameContext type;
		public TerminalNode MethodRef() { return getToken(ExpressionParser.MethodRef, 0); }
		public TerminalNode New() { return getToken(ExpressionParser.New, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public ConstructorReferenceExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterConstructorReferenceExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitConstructorReferenceExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class InstantiationExpressionContext extends ExpressionContext {
		public NameContext type;
		public ArgumentsContext args;
		public TerminalNode New() { return getToken(ExpressionParser.New, 0); }
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public ArgumentsContext arguments() {
			return getRuleContext(ArgumentsContext.class,0);
		}
		public InstantiationExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterInstantiationExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitInstantiationExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IntLitExpressionContext extends ExpressionContext {
		public Token lit;
		public TerminalNode IntLit() { return getToken(ExpressionParser.IntLit, 0); }
		public TerminalNode Minus() { return getToken(ExpressionParser.Minus, 0); }
		public IntLitExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterIntLitExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitIntLitExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ThisExpressionContext extends ExpressionContext {
		public TerminalNode This() { return getToken(ExpressionParser.This, 0); }
		public ThisExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterThisExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitThisExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DecimalLitExpressionContext extends ExpressionContext {
		public Token lit;
		public TerminalNode DecLit() { return getToken(ExpressionParser.DecLit, 0); }
		public TerminalNode Minus() { return getToken(ExpressionParser.Minus, 0); }
		public DecimalLitExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterDecimalLitExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitDecimalLitExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MethodCallExpressionContext extends ExpressionContext {
		public ExpressionContext receiver;
		public NameContext memberName;
		public ArgumentsContext args;
		public TerminalNode Dot() { return getToken(ExpressionParser.Dot, 0); }
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public ArgumentsContext arguments() {
			return getRuleContext(ArgumentsContext.class,0);
		}
		public MethodCallExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterMethodCallExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitMethodCallExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class InstanceofExpressionContext extends ExpressionContext {
		public ExpressionContext expr;
		public NameWithDimsContext type;
		public TerminalNode Instanceof() { return getToken(ExpressionParser.Instanceof, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NameWithDimsContext nameWithDims() {
			return getRuleContext(NameWithDimsContext.class,0);
		}
		public InstanceofExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterInstanceofExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitInstanceofExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WildcardExpressionContext extends ExpressionContext {
		public TerminalNode Wildcard() { return getToken(ExpressionParser.Wildcard, 0); }
		public WildcardExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterWildcardExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitWildcardExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayLitExpressionContext extends ExpressionContext {
		public NameWithDimsContext elementType;
		public NonEmptyArgumentsContext values;
		public TerminalNode New() { return getToken(ExpressionParser.New, 0); }
		public TerminalNode LeftBracket() { return getToken(ExpressionParser.LeftBracket, 0); }
		public TerminalNode RightBracket() { return getToken(ExpressionParser.RightBracket, 0); }
		public TerminalNode LeftBrace() { return getToken(ExpressionParser.LeftBrace, 0); }
		public TerminalNode RightBrace() { return getToken(ExpressionParser.RightBrace, 0); }
		public NameWithDimsContext nameWithDims() {
			return getRuleContext(NameWithDimsContext.class,0);
		}
		public NonEmptyArgumentsContext nonEmptyArguments() {
			return getRuleContext(NonEmptyArgumentsContext.class,0);
		}
		public ArrayLitExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterArrayLitExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitArrayLitExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringLitExpressionContext extends ExpressionContext {
		public Token lit;
		public TerminalNode StringLit() { return getToken(ExpressionParser.StringLit, 0); }
		public StringLitExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterStringLitExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitStringLitExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class EqualityExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public Token op;
		public ExpressionContext right;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode Eq() { return getToken(ExpressionParser.Eq, 0); }
		public TerminalNode Ne() { return getToken(ExpressionParser.Ne, 0); }
		public EqualityExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterEqualityExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitEqualityExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MultiplicativeExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public Token op;
		public ExpressionContext right;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode Mult() { return getToken(ExpressionParser.Mult, 0); }
		public TerminalNode Div() { return getToken(ExpressionParser.Div, 0); }
		public TerminalNode Mod() { return getToken(ExpressionParser.Mod, 0); }
		public MultiplicativeExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterMultiplicativeExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitMultiplicativeExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BitwiseOrExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public ExpressionContext right;
		public TerminalNode BitwiseOr() { return getToken(ExpressionParser.BitwiseOr, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BitwiseOrExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterBitwiseOrExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitBitwiseOrExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParenthesizedExpressionContext extends ExpressionContext {
		public ExpressionContext expr;
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ParenthesizedExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterParenthesizedExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitParenthesizedExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AdditiveExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public Token op;
		public ExpressionContext right;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode Plus() { return getToken(ExpressionParser.Plus, 0); }
		public TerminalNode Minus() { return getToken(ExpressionParser.Minus, 0); }
		public AdditiveExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterAdditiveExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitAdditiveExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberAccessExpressionContext extends ExpressionContext {
		public ExpressionContext receiver;
		public NameContext memberName;
		public TerminalNode Dot() { return getToken(ExpressionParser.Dot, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public MemberAccessExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterMemberAccessExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitMemberAccessExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BoundMethodReferenceExpressionContext extends ExpressionContext {
		public ExpressionContext receiver;
		public NameContext memberName;
		public TerminalNode MethodRef() { return getToken(ExpressionParser.MethodRef, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public BoundMethodReferenceExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterBoundMethodReferenceExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitBoundMethodReferenceExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ShiftExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public Token op;
		public ExpressionContext right;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode Shl() { return getToken(ExpressionParser.Shl, 0); }
		public TerminalNode Shr() { return getToken(ExpressionParser.Shr, 0); }
		public TerminalNode Ushr() { return getToken(ExpressionParser.Ushr, 0); }
		public ShiftExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterShiftExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitShiftExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CapturingExpressionContext extends ExpressionContext {
		public ExpressionContext expr;
		public TerminalNode At() { return getToken(ExpressionParser.At, 0); }
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public CapturingExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterCapturingExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitCapturingExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NullExpressionContext extends ExpressionContext {
		public Token lit;
		public TerminalNode NullLit() { return getToken(ExpressionParser.NullLit, 0); }
		public NullExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterNullExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitNullExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierExpressionContext extends ExpressionContext {
		public Token id;
		public TerminalNode Identifier() { return getToken(ExpressionParser.Identifier, 0); }
		public IdentifierExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterIdentifierExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitIdentifierExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BitwiseAndExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public ExpressionContext right;
		public TerminalNode BitwiseAnd() { return getToken(ExpressionParser.BitwiseAnd, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BitwiseAndExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterBitwiseAndExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitBitwiseAndExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ComparisonExpressionContext extends ExpressionContext {
		public ExpressionContext left;
		public Token op;
		public ExpressionContext right;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode Lt() { return getToken(ExpressionParser.Lt, 0); }
		public TerminalNode Le() { return getToken(ExpressionParser.Le, 0); }
		public TerminalNode Gt() { return getToken(ExpressionParser.Gt, 0); }
		public TerminalNode Ge() { return getToken(ExpressionParser.Ge, 0); }
		public ComparisonExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterComparisonExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitComparisonExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SuperCallExpressionContext extends ExpressionContext {
		public NameContext memberName;
		public ArgumentsContext args;
		public TerminalNode Super() { return getToken(ExpressionParser.Super, 0); }
		public TerminalNode Dot() { return getToken(ExpressionParser.Dot, 0); }
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public ArgumentsContext arguments() {
			return getRuleContext(ArgumentsContext.class,0);
		}
		public SuperCallExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterSuperCallExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitSuperCallExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CastExpressionContext extends ExpressionContext {
		public NameWithDimsContext type;
		public ExpressionContext expr;
		public TerminalNode LeftParen() { return getToken(ExpressionParser.LeftParen, 0); }
		public TerminalNode RightParen() { return getToken(ExpressionParser.RightParen, 0); }
		public NameWithDimsContext nameWithDims() {
			return getRuleContext(NameWithDimsContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public CastExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterCastExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitCastExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NewArrayExpressionContext extends ExpressionContext {
		public NameContext innerType;
		public ExpressionContext expression;
		public List<ExpressionContext> dims = new ArrayList<ExpressionContext>();
		public Token LeftBracket;
		public List<Token> blankDims = new ArrayList<Token>();
		public TerminalNode New() { return getToken(ExpressionParser.New, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public List<TerminalNode> LeftBracket() { return getTokens(ExpressionParser.LeftBracket); }
		public TerminalNode LeftBracket(int i) {
			return getToken(ExpressionParser.LeftBracket, i);
		}
		public List<TerminalNode> RightBracket() { return getTokens(ExpressionParser.RightBracket); }
		public TerminalNode RightBracket(int i) {
			return getToken(ExpressionParser.RightBracket, i);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public NewArrayExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterNewArrayExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitNewArrayExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayAccessExpressionContext extends ExpressionContext {
		public ExpressionContext arr;
		public ExpressionContext index;
		public TerminalNode LeftBracket() { return getToken(ExpressionParser.LeftBracket, 0); }
		public TerminalNode RightBracket() { return getToken(ExpressionParser.RightBracket, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ArrayAccessExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterArrayAccessExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitArrayAccessExpression(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 4;
		enterRecursionRule(_localctx, 4, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(125);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				{
				_localctx = new CapturingExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(42);
				match(At);
				setState(43);
				match(LeftParen);
				setState(44);
				((CapturingExpressionContext)_localctx).expr = expression(0);
				setState(45);
				match(RightParen);
				}
				break;
			case 2:
				{
				_localctx = new WildcardExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(47);
				match(Wildcard);
				}
				break;
			case 3:
				{
				_localctx = new ThisExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(48);
				match(This);
				}
				break;
			case 4:
				{
				_localctx = new IntLitExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(50);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Minus) {
					{
					setState(49);
					((IntLitExpressionContext)_localctx).lit = match(Minus);
					}
				}

				setState(52);
				match(IntLit);
				}
				break;
			case 5:
				{
				_localctx = new DecimalLitExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(54);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Minus) {
					{
					setState(53);
					((DecimalLitExpressionContext)_localctx).lit = match(Minus);
					}
				}

				setState(56);
				match(DecLit);
				}
				break;
			case 6:
				{
				_localctx = new BoolLitExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(57);
				((BoolLitExpressionContext)_localctx).lit = match(BoolLit);
				}
				break;
			case 7:
				{
				_localctx = new NullExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(58);
				((NullExpressionContext)_localctx).lit = match(NullLit);
				}
				break;
			case 8:
				{
				_localctx = new StringLitExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(59);
				((StringLitExpressionContext)_localctx).lit = match(StringLit);
				}
				break;
			case 9:
				{
				_localctx = new IdentifierExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(60);
				((IdentifierExpressionContext)_localctx).id = match(Identifier);
				}
				break;
			case 10:
				{
				_localctx = new ClassConstantExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(61);
				((ClassConstantExpressionContext)_localctx).type = nameWithDims();
				setState(62);
				match(Dot);
				setState(63);
				match(Class);
				}
				break;
			case 11:
				{
				_localctx = new SuperCallExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(65);
				match(Super);
				setState(66);
				match(Dot);
				setState(67);
				((SuperCallExpressionContext)_localctx).memberName = name();
				setState(68);
				match(LeftParen);
				setState(69);
				((SuperCallExpressionContext)_localctx).args = arguments();
				setState(70);
				match(RightParen);
				}
				break;
			case 12:
				{
				_localctx = new StaticMethodCallExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(72);
				((StaticMethodCallExpressionContext)_localctx).memberName = name();
				setState(73);
				match(LeftParen);
				setState(74);
				((StaticMethodCallExpressionContext)_localctx).args = arguments();
				setState(75);
				match(RightParen);
				}
				break;
			case 13:
				{
				_localctx = new FreeMethodReferenceExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(77);
				match(MethodRef);
				setState(78);
				((FreeMethodReferenceExpressionContext)_localctx).memberName = name();
				}
				break;
			case 14:
				{
				_localctx = new ConstructorReferenceExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(79);
				((ConstructorReferenceExpressionContext)_localctx).type = name();
				setState(80);
				match(MethodRef);
				setState(81);
				match(New);
				}
				break;
			case 15:
				{
				_localctx = new UnaryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(83);
				((UnaryExpressionContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==Minus || _la==BitwiseNot) ) {
					((UnaryExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(84);
				((UnaryExpressionContext)_localctx).expr = expression(15);
				}
				break;
			case 16:
				{
				_localctx = new InstantiationExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(85);
				match(New);
				setState(86);
				((InstantiationExpressionContext)_localctx).type = name();
				setState(87);
				match(LeftParen);
				setState(88);
				((InstantiationExpressionContext)_localctx).args = arguments();
				setState(89);
				match(RightParen);
				}
				break;
			case 17:
				{
				_localctx = new ArrayLitExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(91);
				match(New);
				setState(92);
				((ArrayLitExpressionContext)_localctx).elementType = nameWithDims();
				setState(93);
				match(LeftBracket);
				setState(94);
				match(RightBracket);
				setState(95);
				match(LeftBrace);
				setState(96);
				((ArrayLitExpressionContext)_localctx).values = nonEmptyArguments();
				setState(97);
				match(RightBrace);
				}
				break;
			case 18:
				{
				_localctx = new NewArrayExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(99);
				match(New);
				setState(100);
				((NewArrayExpressionContext)_localctx).innerType = name();
				setState(105); 
				_errHandler.sync(this);
				_alt = 1;
				do {
					switch (_alt) {
					case 1:
						{
						{
						setState(101);
						match(LeftBracket);
						setState(102);
						((NewArrayExpressionContext)_localctx).expression = expression(0);
						((NewArrayExpressionContext)_localctx).dims.add(((NewArrayExpressionContext)_localctx).expression);
						setState(103);
						match(RightBracket);
						}
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(107); 
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
				} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
				setState(113);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(109);
						((NewArrayExpressionContext)_localctx).LeftBracket = match(LeftBracket);
						((NewArrayExpressionContext)_localctx).blankDims.add(((NewArrayExpressionContext)_localctx).LeftBracket);
						setState(110);
						match(RightBracket);
						}
						} 
					}
					setState(115);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
				}
				}
				break;
			case 19:
				{
				_localctx = new CastExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(116);
				match(LeftParen);
				setState(117);
				((CastExpressionContext)_localctx).type = nameWithDims();
				setState(118);
				match(RightParen);
				setState(119);
				((CastExpressionContext)_localctx).expr = expression(11);
				}
				break;
			case 20:
				{
				_localctx = new ParenthesizedExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(121);
				match(LeftParen);
				setState(122);
				((ParenthesizedExpressionContext)_localctx).expr = expression(0);
				setState(123);
				match(RightParen);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(174);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(172);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
					case 1:
						{
						_localctx = new MultiplicativeExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((MultiplicativeExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(127);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(128);
						((MultiplicativeExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 7340032L) != 0)) ) {
							((MultiplicativeExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(129);
						((MultiplicativeExpressionContext)_localctx).right = expression(11);
						}
						break;
					case 2:
						{
						_localctx = new AdditiveExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((AdditiveExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(130);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(131);
						((AdditiveExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==Plus || _la==Minus) ) {
							((AdditiveExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(132);
						((AdditiveExpressionContext)_localctx).right = expression(10);
						}
						break;
					case 3:
						{
						_localctx = new ShiftExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((ShiftExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(133);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(134);
						((ShiftExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 60129542144L) != 0)) ) {
							((ShiftExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(135);
						((ShiftExpressionContext)_localctx).right = expression(9);
						}
						break;
					case 4:
						{
						_localctx = new ComparisonExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((ComparisonExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(136);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(137);
						((ComparisonExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1030792151040L) != 0)) ) {
							((ComparisonExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(138);
						((ComparisonExpressionContext)_localctx).right = expression(8);
						}
						break;
					case 5:
						{
						_localctx = new EqualityExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((EqualityExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(139);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(140);
						((EqualityExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==Eq || _la==Ne) ) {
							((EqualityExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(141);
						((EqualityExpressionContext)_localctx).right = expression(6);
						}
						break;
					case 6:
						{
						_localctx = new BitwiseAndExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((BitwiseAndExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(142);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(143);
						match(BitwiseAnd);
						setState(144);
						((BitwiseAndExpressionContext)_localctx).right = expression(5);
						}
						break;
					case 7:
						{
						_localctx = new BitwiseXorExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((BitwiseXorExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(145);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(146);
						match(BitwiseXor);
						setState(147);
						((BitwiseXorExpressionContext)_localctx).right = expression(4);
						}
						break;
					case 8:
						{
						_localctx = new BitwiseOrExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((BitwiseOrExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(148);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(149);
						match(BitwiseOr);
						setState(150);
						((BitwiseOrExpressionContext)_localctx).right = expression(3);
						}
						break;
					case 9:
						{
						_localctx = new ArrayAccessExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((ArrayAccessExpressionContext)_localctx).arr = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(151);
						if (!(precpred(_ctx, 23))) throw new FailedPredicateException(this, "precpred(_ctx, 23)");
						setState(152);
						match(LeftBracket);
						setState(153);
						((ArrayAccessExpressionContext)_localctx).index = expression(0);
						setState(154);
						match(RightBracket);
						}
						break;
					case 10:
						{
						_localctx = new MemberAccessExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((MemberAccessExpressionContext)_localctx).receiver = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(156);
						if (!(precpred(_ctx, 22))) throw new FailedPredicateException(this, "precpred(_ctx, 22)");
						setState(157);
						match(Dot);
						setState(158);
						((MemberAccessExpressionContext)_localctx).memberName = name();
						}
						break;
					case 11:
						{
						_localctx = new MethodCallExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((MethodCallExpressionContext)_localctx).receiver = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(159);
						if (!(precpred(_ctx, 20))) throw new FailedPredicateException(this, "precpred(_ctx, 20)");
						setState(160);
						match(Dot);
						setState(161);
						((MethodCallExpressionContext)_localctx).memberName = name();
						setState(162);
						match(LeftParen);
						setState(163);
						((MethodCallExpressionContext)_localctx).args = arguments();
						setState(164);
						match(RightParen);
						}
						break;
					case 12:
						{
						_localctx = new BoundMethodReferenceExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((BoundMethodReferenceExpressionContext)_localctx).receiver = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(166);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(167);
						match(MethodRef);
						setState(168);
						((BoundMethodReferenceExpressionContext)_localctx).memberName = name();
						}
						break;
					case 13:
						{
						_localctx = new InstanceofExpressionContext(new ExpressionContext(_parentctx, _parentState));
						((InstanceofExpressionContext)_localctx).expr = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(169);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(170);
						match(Instanceof);
						setState(171);
						((InstanceofExpressionContext)_localctx).type = nameWithDims();
						}
						break;
					}
					} 
				}
				setState(176);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NameContext extends ParserRuleContext {
		public NameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name; }
	 
		public NameContext() { }
		public void copyFrom(NameContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WildcardNameContext extends NameContext {
		public TerminalNode Wildcard() { return getToken(ExpressionParser.Wildcard, 0); }
		public WildcardNameContext(NameContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterWildcardName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitWildcardName(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierNameContext extends NameContext {
		public TerminalNode Identifier() { return getToken(ExpressionParser.Identifier, 0); }
		public IdentifierNameContext(NameContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterIdentifierName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitIdentifierName(this);
		}
	}

	public final NameContext name() throws RecognitionException {
		NameContext _localctx = new NameContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_name);
		try {
			setState(179);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case Identifier:
				_localctx = new IdentifierNameContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(177);
				match(Identifier);
				}
				break;
			case Wildcard:
				_localctx = new WildcardNameContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(178);
				match(Wildcard);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NameWithDimsContext extends ParserRuleContext {
		public Token LeftBracket;
		public List<Token> dims = new ArrayList<Token>();
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public List<TerminalNode> RightBracket() { return getTokens(ExpressionParser.RightBracket); }
		public TerminalNode RightBracket(int i) {
			return getToken(ExpressionParser.RightBracket, i);
		}
		public List<TerminalNode> LeftBracket() { return getTokens(ExpressionParser.LeftBracket); }
		public TerminalNode LeftBracket(int i) {
			return getToken(ExpressionParser.LeftBracket, i);
		}
		public NameWithDimsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nameWithDims; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterNameWithDims(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitNameWithDims(this);
		}
	}

	public final NameWithDimsContext nameWithDims() throws RecognitionException {
		NameWithDimsContext _localctx = new NameWithDimsContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_nameWithDims);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(181);
			name();
			setState(186);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(182);
					((NameWithDimsContext)_localctx).LeftBracket = match(LeftBracket);
					((NameWithDimsContext)_localctx).dims.add(((NameWithDimsContext)_localctx).LeftBracket);
					setState(183);
					match(RightBracket);
					}
					} 
				}
				setState(188);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArgumentsContext extends ParserRuleContext {
		public NonEmptyArgumentsContext nonEmptyArguments() {
			return getRuleContext(NonEmptyArgumentsContext.class,0);
		}
		public ArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arguments; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterArguments(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitArguments(this);
		}
	}

	public final ArgumentsContext arguments() throws RecognitionException {
		ArgumentsContext _localctx = new ArgumentsContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_arguments);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(190);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 70373115402680L) != 0)) {
				{
				setState(189);
				nonEmptyArguments();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NonEmptyArgumentsContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(ExpressionParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(ExpressionParser.Comma, i);
		}
		public NonEmptyArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nonEmptyArguments; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).enterNonEmptyArguments(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionParserListener ) ((ExpressionParserListener)listener).exitNonEmptyArguments(this);
		}
	}

	public final NonEmptyArgumentsContext nonEmptyArguments() throws RecognitionException {
		NonEmptyArgumentsContext _localctx = new NonEmptyArgumentsContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_nonEmptyArguments);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(197);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(192);
					expression(0);
					setState(193);
					match(Comma);
					}
					} 
				}
				setState(199);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			}
			setState(200);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 2:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 10);
		case 1:
			return precpred(_ctx, 9);
		case 2:
			return precpred(_ctx, 8);
		case 3:
			return precpred(_ctx, 7);
		case 4:
			return precpred(_ctx, 5);
		case 5:
			return precpred(_ctx, 4);
		case 6:
			return precpred(_ctx, 3);
		case 7:
			return precpred(_ctx, 2);
		case 8:
			return precpred(_ctx, 23);
		case 9:
			return precpred(_ctx, 22);
		case 10:
			return precpred(_ctx, 20);
		case 11:
			return precpred(_ctx, 18);
		case 12:
			return precpred(_ctx, 6);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u00010\u00cb\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001(\b"+
		"\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u00023\b\u0002\u0001"+
		"\u0002\u0001\u0002\u0003\u00027\b\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0004\u0002j\b"+
		"\u0002\u000b\u0002\f\u0002k\u0001\u0002\u0001\u0002\u0005\u0002p\b\u0002"+
		"\n\u0002\f\u0002s\t\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002"+
		"~\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002\u00ad\b\u0002"+
		"\n\u0002\f\u0002\u00b0\t\u0002\u0001\u0003\u0001\u0003\u0003\u0003\u00b4"+
		"\b\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004\u00b9\b\u0004"+
		"\n\u0004\f\u0004\u00bc\t\u0004\u0001\u0005\u0003\u0005\u00bf\b\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0005\u0006\u00c4\b\u0006\n\u0006\f\u0006"+
		"\u00c7\t\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0000\u0001\u0004\u0007"+
		"\u0000\u0002\u0004\u0006\b\n\f\u0000\u0006\u0002\u0000\u0013\u0013\u0017"+
		"\u0017\u0001\u0000\u0014\u0016\u0001\u0000\u0012\u0013\u0001\u0000!#\u0001"+
		"\u0000$\'\u0001\u0000()\u00f0\u0000\u000e\u0001\u0000\u0000\u0000\u0002"+
		"\'\u0001\u0000\u0000\u0000\u0004}\u0001\u0000\u0000\u0000\u0006\u00b3"+
		"\u0001\u0000\u0000\u0000\b\u00b5\u0001\u0000\u0000\u0000\n\u00be\u0001"+
		"\u0000\u0000\u0000\f\u00c5\u0001\u0000\u0000\u0000\u000e\u000f\u0003\u0002"+
		"\u0001\u0000\u000f\u0010\u0005\u0000\u0000\u0001\u0010\u0001\u0001\u0000"+
		"\u0000\u0000\u0011\u0012\u0003\u0004\u0002\u0000\u0012\u0013\u0005\u0018"+
		"\u0000\u0000\u0013\u0014\u0003\u0006\u0003\u0000\u0014\u0015\u0005-\u0000"+
		"\u0000\u0015\u0016\u0003\u0004\u0002\u0000\u0016(\u0001\u0000\u0000\u0000"+
		"\u0017\u0018\u0003\u0004\u0002\u0000\u0018\u0019\u0005\u001c\u0000\u0000"+
		"\u0019\u001a\u0003\u0004\u0002\u0000\u001a\u001b\u0005\u001d\u0000\u0000"+
		"\u001b\u001c\u0005-\u0000\u0000\u001c\u001d\u0003\u0004\u0002\u0000\u001d"+
		"(\u0001\u0000\u0000\u0000\u001e\u001f\u0003\u0006\u0003\u0000\u001f \u0005"+
		"-\u0000\u0000 !\u0003\u0004\u0002\u0000!(\u0001\u0000\u0000\u0000\"#\u0005"+
		"\t\u0000\u0000#(\u0003\u0004\u0002\u0000$%\u0005\n\u0000\u0000%(\u0003"+
		"\u0004\u0002\u0000&(\u0003\u0004\u0002\u0000\'\u0011\u0001\u0000\u0000"+
		"\u0000\'\u0017\u0001\u0000\u0000\u0000\'\u001e\u0001\u0000\u0000\u0000"+
		"\'\"\u0001\u0000\u0000\u0000\'$\u0001\u0000\u0000\u0000\'&\u0001\u0000"+
		"\u0000\u0000(\u0003\u0001\u0000\u0000\u0000)*\u0006\u0002\uffff\uffff"+
		"\u0000*+\u0005 \u0000\u0000+,\u0005\u001a\u0000\u0000,-\u0003\u0004\u0002"+
		"\u0000-.\u0005\u001b\u0000\u0000.~\u0001\u0000\u0000\u0000/~\u0005\u0004"+
		"\u0000\u00000~\u0005\u000b\u0000\u000013\u0005\u0013\u0000\u000021\u0001"+
		"\u0000\u0000\u000023\u0001\u0000\u0000\u000034\u0001\u0000\u0000\u0000"+
		"4~\u0005\u0010\u0000\u000057\u0005\u0013\u0000\u000065\u0001\u0000\u0000"+
		"\u000067\u0001\u0000\u0000\u000078\u0001\u0000\u0000\u00008~\u0005\u0011"+
		"\u0000\u00009~\u0005\u0007\u0000\u0000:~\u0005\b\u0000\u0000;~\u0005\u0003"+
		"\u0000\u0000<~\u0005\u000f\u0000\u0000=>\u0003\b\u0004\u0000>?\u0005\u0018"+
		"\u0000\u0000?@\u0005\r\u0000\u0000@~\u0001\u0000\u0000\u0000AB\u0005\f"+
		"\u0000\u0000BC\u0005\u0018\u0000\u0000CD\u0003\u0006\u0003\u0000DE\u0005"+
		"\u001a\u0000\u0000EF\u0003\n\u0005\u0000FG\u0005\u001b\u0000\u0000G~\u0001"+
		"\u0000\u0000\u0000HI\u0003\u0006\u0003\u0000IJ\u0005\u001a\u0000\u0000"+
		"JK\u0003\n\u0005\u0000KL\u0005\u001b\u0000\u0000L~\u0001\u0000\u0000\u0000"+
		"MN\u0005.\u0000\u0000N~\u0003\u0006\u0003\u0000OP\u0003\u0006\u0003\u0000"+
		"PQ\u0005.\u0000\u0000QR\u0005\u0005\u0000\u0000R~\u0001\u0000\u0000\u0000"+
		"ST\u0007\u0000\u0000\u0000T~\u0003\u0004\u0002\u000fUV\u0005\u0005\u0000"+
		"\u0000VW\u0003\u0006\u0003\u0000WX\u0005\u001a\u0000\u0000XY\u0003\n\u0005"+
		"\u0000YZ\u0005\u001b\u0000\u0000Z~\u0001\u0000\u0000\u0000[\\\u0005\u0005"+
		"\u0000\u0000\\]\u0003\b\u0004\u0000]^\u0005\u001c\u0000\u0000^_\u0005"+
		"\u001d\u0000\u0000_`\u0005\u001e\u0000\u0000`a\u0003\f\u0006\u0000ab\u0005"+
		"\u001f\u0000\u0000b~\u0001\u0000\u0000\u0000cd\u0005\u0005\u0000\u0000"+
		"di\u0003\u0006\u0003\u0000ef\u0005\u001c\u0000\u0000fg\u0003\u0004\u0002"+
		"\u0000gh\u0005\u001d\u0000\u0000hj\u0001\u0000\u0000\u0000ie\u0001\u0000"+
		"\u0000\u0000jk\u0001\u0000\u0000\u0000ki\u0001\u0000\u0000\u0000kl\u0001"+
		"\u0000\u0000\u0000lq\u0001\u0000\u0000\u0000mn\u0005\u001c\u0000\u0000"+
		"np\u0005\u001d\u0000\u0000om\u0001\u0000\u0000\u0000ps\u0001\u0000\u0000"+
		"\u0000qo\u0001\u0000\u0000\u0000qr\u0001\u0000\u0000\u0000r~\u0001\u0000"+
		"\u0000\u0000sq\u0001\u0000\u0000\u0000tu\u0005\u001a\u0000\u0000uv\u0003"+
		"\b\u0004\u0000vw\u0005\u001b\u0000\u0000wx\u0003\u0004\u0002\u000bx~\u0001"+
		"\u0000\u0000\u0000yz\u0005\u001a\u0000\u0000z{\u0003\u0004\u0002\u0000"+
		"{|\u0005\u001b\u0000\u0000|~\u0001\u0000\u0000\u0000})\u0001\u0000\u0000"+
		"\u0000}/\u0001\u0000\u0000\u0000}0\u0001\u0000\u0000\u0000}2\u0001\u0000"+
		"\u0000\u0000}6\u0001\u0000\u0000\u0000}9\u0001\u0000\u0000\u0000}:\u0001"+
		"\u0000\u0000\u0000};\u0001\u0000\u0000\u0000}<\u0001\u0000\u0000\u0000"+
		"}=\u0001\u0000\u0000\u0000}A\u0001\u0000\u0000\u0000}H\u0001\u0000\u0000"+
		"\u0000}M\u0001\u0000\u0000\u0000}O\u0001\u0000\u0000\u0000}S\u0001\u0000"+
		"\u0000\u0000}U\u0001\u0000\u0000\u0000}[\u0001\u0000\u0000\u0000}c\u0001"+
		"\u0000\u0000\u0000}t\u0001\u0000\u0000\u0000}y\u0001\u0000\u0000\u0000"+
		"~\u00ae\u0001\u0000\u0000\u0000\u007f\u0080\n\n\u0000\u0000\u0080\u0081"+
		"\u0007\u0001\u0000\u0000\u0081\u00ad\u0003\u0004\u0002\u000b\u0082\u0083"+
		"\n\t\u0000\u0000\u0083\u0084\u0007\u0002\u0000\u0000\u0084\u00ad\u0003"+
		"\u0004\u0002\n\u0085\u0086\n\b\u0000\u0000\u0086\u0087\u0007\u0003\u0000"+
		"\u0000\u0087\u00ad\u0003\u0004\u0002\t\u0088\u0089\n\u0007\u0000\u0000"+
		"\u0089\u008a\u0007\u0004\u0000\u0000\u008a\u00ad\u0003\u0004\u0002\b\u008b"+
		"\u008c\n\u0005\u0000\u0000\u008c\u008d\u0007\u0005\u0000\u0000\u008d\u00ad"+
		"\u0003\u0004\u0002\u0006\u008e\u008f\n\u0004\u0000\u0000\u008f\u0090\u0005"+
		"*\u0000\u0000\u0090\u00ad\u0003\u0004\u0002\u0005\u0091\u0092\n\u0003"+
		"\u0000\u0000\u0092\u0093\u0005+\u0000\u0000\u0093\u00ad\u0003\u0004\u0002"+
		"\u0004\u0094\u0095\n\u0002\u0000\u0000\u0095\u0096\u0005,\u0000\u0000"+
		"\u0096\u00ad\u0003\u0004\u0002\u0003\u0097\u0098\n\u0017\u0000\u0000\u0098"+
		"\u0099\u0005\u001c\u0000\u0000\u0099\u009a\u0003\u0004\u0002\u0000\u009a"+
		"\u009b\u0005\u001d\u0000\u0000\u009b\u00ad\u0001\u0000\u0000\u0000\u009c"+
		"\u009d\n\u0016\u0000\u0000\u009d\u009e\u0005\u0018\u0000\u0000\u009e\u00ad"+
		"\u0003\u0006\u0003\u0000\u009f\u00a0\n\u0014\u0000\u0000\u00a0\u00a1\u0005"+
		"\u0018\u0000\u0000\u00a1\u00a2\u0003\u0006\u0003\u0000\u00a2\u00a3\u0005"+
		"\u001a\u0000\u0000\u00a3\u00a4\u0003\n\u0005\u0000\u00a4\u00a5\u0005\u001b"+
		"\u0000\u0000\u00a5\u00ad\u0001\u0000\u0000\u0000\u00a6\u00a7\n\u0012\u0000"+
		"\u0000\u00a7\u00a8\u0005.\u0000\u0000\u00a8\u00ad\u0003\u0006\u0003\u0000"+
		"\u00a9\u00aa\n\u0006\u0000\u0000\u00aa\u00ab\u0005\u0006\u0000\u0000\u00ab"+
		"\u00ad\u0003\b\u0004\u0000\u00ac\u007f\u0001\u0000\u0000\u0000\u00ac\u0082"+
		"\u0001\u0000\u0000\u0000\u00ac\u0085\u0001\u0000\u0000\u0000\u00ac\u0088"+
		"\u0001\u0000\u0000\u0000\u00ac\u008b\u0001\u0000\u0000\u0000\u00ac\u008e"+
		"\u0001\u0000\u0000\u0000\u00ac\u0091\u0001\u0000\u0000\u0000\u00ac\u0094"+
		"\u0001\u0000\u0000\u0000\u00ac\u0097\u0001\u0000\u0000\u0000\u00ac\u009c"+
		"\u0001\u0000\u0000\u0000\u00ac\u009f\u0001\u0000\u0000\u0000\u00ac\u00a6"+
		"\u0001\u0000\u0000\u0000\u00ac\u00a9\u0001\u0000\u0000\u0000\u00ad\u00b0"+
		"\u0001\u0000\u0000\u0000\u00ae\u00ac\u0001\u0000\u0000\u0000\u00ae\u00af"+
		"\u0001\u0000\u0000\u0000\u00af\u0005\u0001\u0000\u0000\u0000\u00b0\u00ae"+
		"\u0001\u0000\u0000\u0000\u00b1\u00b4\u0005\u000f\u0000\u0000\u00b2\u00b4"+
		"\u0005\u0004\u0000\u0000\u00b3\u00b1\u0001\u0000\u0000\u0000\u00b3\u00b2"+
		"\u0001\u0000\u0000\u0000\u00b4\u0007\u0001\u0000\u0000\u0000\u00b5\u00ba"+
		"\u0003\u0006\u0003\u0000\u00b6\u00b7\u0005\u001c\u0000\u0000\u00b7\u00b9"+
		"\u0005\u001d\u0000\u0000\u00b8\u00b6\u0001\u0000\u0000\u0000\u00b9\u00bc"+
		"\u0001\u0000\u0000\u0000\u00ba\u00b8\u0001\u0000\u0000\u0000\u00ba\u00bb"+
		"\u0001\u0000\u0000\u0000\u00bb\t\u0001\u0000\u0000\u0000\u00bc\u00ba\u0001"+
		"\u0000\u0000\u0000\u00bd\u00bf\u0003\f\u0006\u0000\u00be\u00bd\u0001\u0000"+
		"\u0000\u0000\u00be\u00bf\u0001\u0000\u0000\u0000\u00bf\u000b\u0001\u0000"+
		"\u0000\u0000\u00c0\u00c1\u0003\u0004\u0002\u0000\u00c1\u00c2\u0005\u0019"+
		"\u0000\u0000\u00c2\u00c4\u0001\u0000\u0000\u0000\u00c3\u00c0\u0001\u0000"+
		"\u0000\u0000\u00c4\u00c7\u0001\u0000\u0000\u0000\u00c5\u00c3\u0001\u0000"+
		"\u0000\u0000\u00c5\u00c6\u0001\u0000\u0000\u0000\u00c6\u00c8\u0001\u0000"+
		"\u0000\u0000\u00c7\u00c5\u0001\u0000\u0000\u0000\u00c8\u00c9\u0003\u0004"+
		"\u0002\u0000\u00c9\r\u0001\u0000\u0000\u0000\f\'26kq}\u00ac\u00ae\u00b3"+
		"\u00ba\u00be\u00c5";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}