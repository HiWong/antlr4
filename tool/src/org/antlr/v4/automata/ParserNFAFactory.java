package org.antlr.v4.automata;


import org.antlr.v4.misc.IntSet;
import org.antlr.v4.tool.*;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;

// TODO: investigate o-X->o for basic states with typename for transition

/** Superclass of NFABuilder.g that provides actual NFA construction routines. */
public class ParserNFAFactory implements NFAFactory {
	public Grammar g;
	public Rule currentRule;
	NFA nfa;

	public ParserNFAFactory(Grammar g) { this.g = g; nfa = new NFA(g); }

	public NFA getNFA() {
		addEOFStates(g.rules.values());
		return null;
	}

	/** add an EOF transition to any rule end NFAState that points to nothing
     *  (i.e., for all those rules not invoked by another rule).  These
     *  are start symbols then.
	 *
	 *  Return the number of grammar entry points; i.e., how many rules are
	 *  not invoked by another rule (they can only be invoked from outside).
	 *  These are the start rules.
     */
	public int addEOFStates(Collection<Rule> rules) { return 0; }


	
	public Rule getCurrentRule() { return currentRule; }

	public void setCurrentRuleName(String name) {
		this.currentRule = g.getRule(name);
	}

	public NFAState newState(Class nodeType, GrammarAST node) {
		try {
			Constructor ctor = nodeType.getConstructor(NFA.class);
			NFAState s = (NFAState)ctor.newInstance(nfa);
			s.ast = node;
			nfa.addState(s);
			return s;
		}
		catch (Exception e) {
			ErrorManager.internalError("can't create NFA node: "+nodeType.getName(), e);
		}
		return null;
	}

	public BasicState newState(GrammarAST node) {
		BasicState n = new BasicState(nfa);
		n.ast = node;
		nfa.addState(n);
		return n;
	}

	public BasicState newState() { return newState(null); }

	/** From label A build Graph o-A->o */
	public Handle tokenRef(TerminalAST node) {
		System.out.println("tokenRef: "+node);
		BasicState left = newState(node);
		BasicState right = newState(node);
		int ttype = g.getTokenType(node.getText());
		left.transition = new AtomTransition(ttype, right);
		return new Handle(left, right);
	}

	/** From set build single edge graph o->o-set->o.  To conform to
     *  what an alt block looks like, must have extra state on left.
     */
	public Handle set(IntSet set, GrammarAST associatedAST) { return null; }

	public Handle tree(List<Handle> els) {
		return null;
	}

	public Handle range(GrammarAST a, GrammarAST b) { return null; }

	public Handle not(Handle A) {
		return null;
	}

	/** From char 'c' build o-intValue(c)->o
	 */
	public Handle charLiteral(GrammarAST charLiteralAST) { return null; }

	/** For a non-lexer, just build a simple token reference atom.
	 *  For a lexer, a string is a sequence of char to match.  That is,
	 *  "fog" is treated as 'f' 'o' 'g' not as a single transition in
	 *  the DFA.  Machine== o-'f'->o-'o'->o-'g'->o and has n+1 states
	 *  for n characters.
	 */
	public Handle stringLiteral(GrammarAST stringLiteralAST) {
		System.out.println("stringLiteral: "+stringLiteralAST);
		return null;
	}

	/** For reference to rule r, build
	 *
	 *  o-e->(r)  o
	 *
	 *  where (r) is the start of rule r and the trailing o is not linked
	 *  to from rule ref state directly (it's done thru the transition(0)
	 *  RuleClosureTransition.
	 *
	 *  If the rule r is just a list of tokens, it's block will be just
	 *  a set on an edge o->o->o-set->o->o->o, could inline it rather than doing
	 *  the rule reference, but i'm not doing this yet as I'm not sure
	 *  it would help much in the NFA->DFA construction.
	 *
	 *  TODO add to codegen: collapse alt blks that are sets into single matchSet
	 * @param node
	 */
	public Handle ruleRef(GrammarAST node) { return null; }

	/** From an empty alternative build  o-e->o */
	public Handle epsilon() { return null; }

	/** Build what amounts to an epsilon transition with a semantic
	 *  predicate action.  The pred is a pointer into the AST of
	 *  the SEMPRED token.
	 */
	public Handle sempred(GrammarAST pred) {
		System.out.println("sempred: "+ pred);
		BasicState left = newState(pred);
		NFAState right = newState(pred);
		left.transition = new PredicateTransition(pred, right);
		return new Handle(left, right);
	}

	public Handle gated_sempred(GrammarAST pred) {
		return null;
	}

	/** Build what amounts to an epsilon transition with an action.
	 *  The action goes into NFA though it is ignored during analysis.
	 *  It slows things down a bit, but I must ignore predicates after
	 *  having seen an action (5-5-2008).
	 */
	public Handle action(GrammarAST action) {
		System.out.println("action: "+action);
		BasicState left = newState(action);
		NFAState right = newState(action);
		left.transition = new ActionTransition(action, right);  
		return new Handle(left, right);
	}

	/** From A B build A-e->B (that is, build an epsilon arc from right
	 *  of A to left of B).
	 *
	 *  As a convenience, return B if A is null or return A if B is null.
	 */
	public Handle sequence(Handle A, Handle B) { return null; }

	/** From a set ('a'|'b') build
     *
     *  o->o-'a'..'b'->o->o (last NFAState is blockEndNFAState pointed to by all alts)
	 */
	public Handle blockFromSet(Handle set) { return null; }

	/** From A|B|..|Z alternative block build
     *
     *  o->o-A->o->o (last NFAState is BlockEndState pointed to by all alts)
     *  |          ^
     *  |->o-B->o--|
     *  |          |
     *  ...        |
     *  |          |
     *  |->o-Z->o--|
     *
     *  So start node points at every alternative with epsilon transition
     *  and every alt right side points at a block end NFAState.
     *
     *  Special case: only one alternative: don't make a block with alt
     *  begin/end.
     *
     *  Special case: if just a list of tokens/chars/sets, then collapse
     *  to a single edge'd o-set->o graph.
     *
     *  TODO: Set alt number (1..n) in the states?
     */
	public Handle block(GrammarAST blkAST, List<Handle> alts) {
		System.out.println("block: "+alts);
		if ( alts.size()==1 ) return alts.get(0);
				
		BlockStartState start = (BlockStartState)newState(BlockStartState.class, blkAST);
		BlockEndState end = (BlockEndState)newState(BlockEndState.class, blkAST);
		for (Handle alt : alts) {
			epsilon(start, alt.left);
			epsilon(alt.right, end);
		}
		Handle h = new Handle(start, end);
		FASerializer ser = new FASerializer(g, h.left);
		nfa.defineDecisionState(start);
		System.out.println(blkAST.toStringTree()+":\n"+ser);
		return h;
	}

	public Handle alt(List<Handle> els) {
		Handle first = els.get(0);
		Handle last = els.get(els.size()-1);
		return new Handle(first.left, last.right);
	}

	/** From (A)? build either:
	 *
	 *  o--A->o
	 *  |     ^
	 *  o---->|
	 *
	 *  or, if A is a block, just add an empty alt to the end of the block
	 */
	public Handle optional(GrammarAST optAST, Handle blk) {
		if ( blk.left instanceof BlockStartState ) {
			epsilon(blk.left, blk.right);
			FASerializer ser = new FASerializer(g, blk.left);
			System.out.println(optAST.toStringTree()+":\n"+ser);
			return blk;
		}

		// construct block
		BlockStartState start = (BlockStartState)newState(BlockStartState.class, optAST);
		BlockEndState end = (BlockEndState)newState(BlockEndState.class, optAST);
		epsilon(start, blk.left);
		epsilon(blk.right, end);
		epsilon(start, end);

		nfa.defineDecisionState(start);

		Handle h = new Handle(start, end);
		FASerializer ser = new FASerializer(g, h.left);
		System.out.println(optAST.toStringTree()+":\n"+ser);
		return h;
	}

	/** From (A)+ build
	 *
	 *     |---|    (Transition 2 from A.right points at alt 1)
	 *     v   |    (follow of loop is Transition 1)
	 *  o->o-A-o->o
	 *
	 *  Meaning that the last NFAState in A points back to A's left Transition NFAState
	 *  and we add a new begin/end NFAState.  A can be single alternative or
	 *  multiple.
	 *
	 *  During analysis we'll call the follow link (transition 1) alt n+1 for
	 *  an n-alt A block.
	 */
	public Handle plus(GrammarAST plusAST, Handle blk) { return null; }

	/** From (A)* build
	 *
	 *     |---|
	 *     v   |
	 *  o->o-A-o--o (Transition 2 from block end points at alt 1; follow is Transition 1)
	 *  |         ^
	 *  o---------| (optional branch is 2nd alt of optional block containing A+)
	 *
	 *  Meaning that the last (end) NFAState in A points back to A's
	 *  left side NFAState and we add 3 new NFAStates (the
	 *  optional branch is built just like an optional subrule).
	 *  See the Aplus() method for more on the loop back Transition.
	 *  The new node on right edge is set to RIGHT_EDGE_OF_CLOSURE so we
	 *  can detect nested (A*)* loops and insert an extra node.  Previously,
	 *  two blocks shared same EOB node.
	 *
	 *  There are 2 or 3 decision points in a A*.  If A is not a block (i.e.,
	 *  it only has one alt), then there are two decisions: the optional bypass
	 *  and then loopback.  If A is a block of alts, then there are three
	 *  decisions: bypass, loopback, and A's decision point.
	 *
	 *  Note that the optional bypass must be outside the loop as (A|B)* is
	 *  not the same thing as (A|B|)+.
	 *
	 *  This is an accurate NFA representation of the meaning of (A)*, but
	 *  for generating code, I don't need a DFA for the optional branch by
	 *  virtue of how I generate code.  The exit-loopback-branch decision
	 *  is sufficient to let me make an appropriate enter, exit, loop
	 *  determination.  See codegen.g
	 */
	public Handle star(GrammarAST starAST, Handle blk) { return null; }

	/** Build an atom with all possible values in its label */
	public Handle wildcard(GrammarAST associatedAST) { return null; }

	/** Build a subrule matching ^(. .*) (any tree or node). Let's use
	 *  (^(. .+) | .) to be safe.
	 */
	public Handle wildcardTree(GrammarAST associatedAST) { return null; }

	void epsilon(NFAState a, NFAState b) {
		a.addTransition(new EpsilonTransition(b));
	}
}